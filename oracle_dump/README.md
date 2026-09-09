# Oracle Dump Importer

A multi-threaded Spring Boot service that watches client directories for Oracle `.dmp` files,
waits until each file has finished copying, verifies it by SHA-256, de-duplicates by
`client + content`, and runs a **bounded pool of import workers**. Built to run as a **Windows
Service** against local drives and UNC shares — see [`CLAUDE.md`](CLAUDE.md) for the full runtime
contract that drove the design.

Real Oracle `impdp.exe` execution is intentionally left as a `TODO` (`CLAUDE.md` §10); the default
`mock` importer exercises the whole pipeline safely.

---

## Pipeline

```
                 ┌─────────────┐   fixedDelay    ┌──────────────────────┐
 dump dir  ─────▶│ ScanScheduler│───────────────▶│ DirectoryScanner     │  1 tx per client
 (local/UNC)     └─────────────┘                 │  └ ClientDirectoryScanner
                                                 └──────────┬───────────┘
                                                            │ stable N scans + quiet period
                                                            ▼
                                              DumpStatus.PENDING_IMPORT   (metadata DB)
                                                            │
                 ┌────────────────┐  fixedDelay             ▼
                 │ ImportDispatcher│──── claim (atomic, status-guarded UPDATE) ──┐
                 └────────────────┘                                             │
                        │ only claims up to free worker capacity               │
                        ▼                                                      │
             ThreadPoolTaskExecutor  (core = max = maxWorkers, bounded queue)  │
                        │                                                      │
                        ▼                                                      │
                 ┌──────────────┐   beginChecksum → [hash] → completeChecksum → [impdp] → record
                 │ DumpProcessor │   (each DB step is its own short transaction)
                 └──────────────┘   per-client Semaphore enforces maxParallelImports
```

### State machine (`DumpStatus`)

`DISCOVERED → STABILIZING → PENDING_IMPORT → QUEUED → CHECKSUMMING → IMPORTING → IMPORTED`

with `DUPLICATE`, `FAILED`, `MISSING` outcomes and retry edges back to `PENDING_IMPORT`.
Transitions are declared once and enforced by `DumpFileRecord.transitionTo(...)`.

## Robustness

| Concern | Handling |
|---|---|
| Copy in progress | N consecutive stable scans (size + mtime) **and** a quiet period; `*.part/*.tmp/*.partial/*.copying` ignored (`CLAUDE.md` §6–§7) |
| Windows / UNC IO errors | `IoFailure` classifies "used by another process", "network name no longer available", `AccessDeniedException`, … into retryable / non-retryable (`CLAUDE.md` §24–§25) |
| Transient failures | Exponential backoff with ceiling (`RetryPolicy`), persisted on the record (`attemptCount`, `nextEligibleAt`) |
| Crash mid-import | `StaleRecordReaper` resets in-flight records older than `stale-processing-timeout`, plus a full sweep on startup |
| Concurrency | Atomic status-guarded `claim` UPDATE + `@Version` optimistic locking → safe across threads and instances |
| Back-pressure | Dispatcher only claims what the bounded executor can accept |
| Graceful shutdown | `ProcessingLifecycle` (SmartLifecycle) stops intake, drains in-flight imports within `shutdown-grace-period` (`CLAUDE.md` §20) |
| Big files | Streaming SHA-256, stored hash reused while path + size + mtime are unchanged (`CLAUDE.md` §18) |
| Duplicates | `client + sha256` unique-ish; a re-import short-circuits to `DUPLICATE` |
| Oracle CLI missing | `OracleClientValidator` checks `impdp.exe`/`imp.exe` at startup; surfaced via health endpoint (`CLAUDE.md` §13) |

## Design notes

- **Strategy** via a functional `DumpImporter` interface; `ImporterConfig` picks the bean whose
  `mode()` matches `oracle-import.importer.mode`, so switching to real `impdp` is config-only.
- **Sealed result types** — `ImportResult.{Success,Failure}` and
  `ProcessingSteps.ImportDecision.{Proceed,Duplicate,Aborted}` — pattern-matched with `switch`.
- Lambda-driven: `IoFailure` rules are `Predicate<Throwable>` + kind; `Retry` is a fluent
  `Supplier`-based helper; scanner/dispatcher use stream pipelines throughout.
- Slow work (hashing, import) never runs inside a DB transaction — `ProcessingSteps` holds only the
  short transactional steps; `TransactionTemplate` scopes each claim.
- `DumpStagingService` is a **seam only** (interface, no impl) for the future
  Spring-dir → Oracle `DIRECTORY` staging copy (`CLAUDE.md` §15–§16).

## Run

```bash
# dev: in-memory H2, local scan dirs auto-created, mock importer
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# then drop a .dmp into ./var/oracle-dumps/client-a and watch the logs
```

```bash
./mvnw test          # 40 tests: unit + full end-to-end pipeline (PipelineIntegrationTest)
./mvnw spring-boot:run   # default profile, file-based H2, no clients configured
```

## Documentation

- [`architecture.md`](architecture.md) — full architecture with diagrams, design rationale, config reference, Windows deployment.
- [`runbook.md`](runbook.md) — step-by-step build / run / feed / verify / recover guide with screenshots.
- Diagrams and screenshots live in [`images/`](images/).

### Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/status` | counts by `DumpStatus`, importer mode, worker count |
| `GET /api/dumps?status=FAILED&limit=50` | recent records |
| `POST /api/dumps/{id}/retry` | requeue a `FAILED` / `MISSING` dump |
| `GET /actuator/health` | includes `dumpDirectories` and `oracleClient` indicators |

## Configuration (`application.yaml`, prefix `oracle-import`)

```yaml
oracle-import:
  scanner:      { interval: 30s, stable-file-check-delay: 30s, stable-scans-required: 2 }
  processing:   { max-workers: 4, poll-interval: 10s, max-attempts: 5,
                  retry-backoff: 1m, retry-backoff-max: 1h, stale-processing-timeout: 2h,
                  shutdown-grace-period: 2m }
  checksum:     { algorithm: SHA-256, buffer-size-mb: 8 }
  oracle-client:{ impdp-path: "C:/Oracle/.../impdp.exe", oracle-home: "...", tns-admin: "..." }
  importer:     { mode: mock, import-log-root: "./logs/imports" }
  clients:
    - client-id: client-a
      dump-directory: "//nas01/oracle-dumps/client-a"   # UNC or local; never a mapped drive
      source-schema: APP
      target-schema: CLIENT_A
      oracle-directory-name: CLIENT_A_IMPORT_DIR
      max-parallel-imports: 1
```
