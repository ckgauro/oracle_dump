# CLAUDE.md — Oracle Dump Checksum Service

Technical specification for an AI coding assistant (or any engineer) implementing this project. This document defines scope, architecture, and conventions. It does not contain runnable code — implementation follows this spec.

## 1. Purpose

A Spring Boot service that, for a set of clients, scans each client's Oracle dump file directory, computes a checksum for every dump file it finds, and persists the result. The core engineering problem is speed and correctness: checksum many (potentially large) files as fast as possible using multiple threads, while guaranteeing that a file already picked up for processing — or already processed — is never picked up again.

## 2. Objectives (source requirements)

1. Java 21, Spring Boot 4.1.1, Lombok.
2. Support multiple clients, each with multiple Oracle dump files.
3. Compute checksums as fast as possible using multithreading, with a hard guarantee against re-picking a file that has already been claimed or processed.
4. Use H2 as the database for the current demo phase.
5. Client file locations differ per client and are not hardcoded — they are derived from H2-stored configuration, so paths/clients can change or be added at runtime without a redeploy.
6. Base package: `com.oracle.checksum`.
7. One default `application.yaml` for now (no per-profile files yet).
8. Code samples in this spec omit `import` statements — imports are an implementation detail to be filled in later, not a spec concern.

## 3. Tech Stack

- Java 21 (use virtual threads where the workload is I/O-bound — see §6)
- Spring Boot 4.1.1
  - Spring Web (status/trigger endpoints)
  - Spring Data JPA
  - Spring Scheduling (`@EnableScheduling`)
- Lombok (`@Getter`/`@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j` on services)
- H2 (file-based or in-memory, see §7.4) as the only datastore for this phase
- Build tool: Maven or Gradle (unspecified — pick one and stay consistent; examples below assume Maven layout)

## 4. Package Layout

```
com.oracle.checksum
├── ChecksumApplication          (main class)
├── config
│   ├── AsyncExecutorConfig      (thread pool / virtual thread executor bean)
│   └── SchedulingConfig
├── domain
│   ├── Client                   (JPA entity)
│   ├── ClientFileLocation       (JPA entity — one row per watched path per client)
│   └── DumpFileRecord           (JPA entity — one row per discovered file + its checksum/status)
├── repository
│   ├── ClientRepository
│   ├── ClientFileLocationRepository
│   └── DumpFileRecordRepository
├── service
│   ├── ClientConfigService      (reads client/path config from H2)
│   ├── FileDiscoveryService     (walks configured paths, finds candidate files)
│   ├── ChecksumClaimService     (atomically claims a file before processing — the anti-double-pickup guard)
│   └── ChecksumComputationService (does the actual hashing, multithreaded)
├── scheduler
│   └── ChecksumScanScheduler    (periodic trigger of discovery + checksum run)
└── web
    └── ChecksumStatusController (optional: trigger a run / view status)
```

## 5. Data Model (H2)

All configuration and state lives in H2 for this phase — nothing is hardcoded in YAML or Java constants.

### 5.1 `client`
| column | type | notes |
|---|---|---|
| id | BIGINT PK | |
| code | VARCHAR, unique | short client identifier |
| name | VARCHAR | display name |
| active | BOOLEAN | disable a client without deleting it |

### 5.2 `client_file_location`
| column | type | notes |
|---|---|---|
| id | BIGINT PK | |
| client_id | BIGINT FK → client.id | |
| base_path | VARCHAR | absolute directory to scan for this client's dump files |
| file_pattern | VARCHAR | e.g. `*.dmp`; default if null |
| active | BOOLEAN | lets you add/retire a path per client at runtime |

This is the table that satisfies requirement 5: adding a new client, or changing where a client's dumps live, is an insert/update in this table — no config file or redeploy needed. `ClientConfigService` reads this table (with a short in-memory cache and TTL, or a manual refresh endpoint) on every scan cycle so changes take effect on the next run.

### 5.3 `dump_file_record`
| column | type | notes |
|---|---|---|
| id | BIGINT PK | |
| client_id | BIGINT FK → client.id | |
| file_path | VARCHAR | absolute path, unique together with client_id |
| file_size | BIGINT | captured at discovery time |
| last_modified | TIMESTAMP | captured at discovery time |
| checksum_algorithm | VARCHAR | e.g. `SHA-256` |
| checksum_value | VARCHAR | null until computed |
| status | VARCHAR | `DISCOVERED` \| `CLAIMED` \| `COMPLETED` \| `FAILED` |
| claimed_by | VARCHAR | worker/thread identifier, for diagnostics |
| claimed_at | TIMESTAMP | |
| completed_at | TIMESTAMP | |

Unique constraint on `(client_id, file_path)`. This is what makes "already picked up" a database fact, not an in-memory one — safe even if the app restarts mid-run.

## 6. Concurrency Design — "fastest way to checksum, never pick up twice"

Two separate concerns, handled separately:

**a. Parallelism across files.** Dump files are typically large and the work is dominated by disk I/O plus digest computation. Use a dedicated executor, not the common ForkJoinPool:

- Default: `Executors.newVirtualThreadPerTaskExecutor()` (Java 21). Virtual threads suit this well because the bottleneck per file is blocking I/O (reading the file in chunks); you can have far more concurrent file-reads in flight than physical cores without exhausting platform threads.
- Alternative to note in the code (as a config toggle, not a rewrite): a fixed platform-thread pool sized to `Runtime.getRuntime().availableProcessors()` for cases where digest computation (not I/O) turns out to be the bottleneck on the target hardware — this is a tuning decision to validate with real dump files, not a default to build now.
- Read files via `FileChannel` + a reused direct `ByteBuffer` (e.g. 8–16 MB chunks) feeding a `MessageDigest` instance. Each thread/task must use its own `MessageDigest` instance — it is not thread-safe to share.
- Default algorithm: `SHA-256`. Make it a config value (`checksum.algorithm`), not a constant, since clients may eventually require a different algorithm.

**b. Exactly-once pickup (the "don't process an already-picked-up file" requirement).** This is a claim pattern enforced at the database, so it holds even across multiple app instances or threads racing each other:

1. `FileDiscoveryService` walks each active `client_file_location.base_path` and, for every matching file not already present in `dump_file_record` for that client+path, inserts a row with `status = DISCOVERED`.
2. Each worker thread claims work with an atomic conditional update, not a read-then-write:
   `UPDATE dump_file_record SET status = 'CLAIMED', claimed_by = ?, claimed_at = ? WHERE id = ? AND status = 'DISCOVERED'`.
   The update only succeeds (affects 1 row) if no other thread claimed it first — this is the guard against double pickup, and it is race-safe under H2's default transaction isolation without needing an explicit lock.
3. Only a thread whose claim update actually affected a row proceeds to compute the checksum for that file. On success it writes `checksum_value`, `status = COMPLETED`, `completed_at`. On failure it writes `status = FAILED` (with a reason, if a `failure_reason` column is added later) rather than leaving it `CLAIMED` forever.
4. A file already `COMPLETED` is never re-discovered as a candidate — the discovery query in step 1 excludes any `(client_id, file_path)` already present in the table, regardless of status.
5. Optional hardening for a later phase: a periodic sweep that resets stale `CLAIMED` rows (claimed_at older than N minutes with no completion) back to `DISCOVERED`, to recover from a worker crash mid-processing. Worth flagging in code comments even if not built in this phase.

## 7. Configuration

### 7.1 Single `application.yaml`

Per requirement 7, there is exactly one default configuration file — no `application-dev.yaml` / `application-prod.yaml` split yet. It should define:

- H2 datasource (file-based, e.g. `jdbc:h2:file:./data/checksum-db`, so state survives restarts) and the H2 console toggle for demo inspection.
- JPA/Hibernate `ddl-auto` (e.g. `update` for this phase).
- `checksum.algorithm` (default `SHA-256`).
- `checksum.executor.type` (`virtual` default, `fixed` alternative) and `checksum.executor.fixed-pool-size` (used only when type is `fixed`).
- A cron/interval for `ChecksumScanScheduler` (e.g. `checksum.scan.cron`).

### 7.2 Client/path configuration is data, not YAML

Per requirement 5, client-to-path mapping is **not** in `application.yaml` — it lives in the `client` and `client_file_location` tables (§5.1–5.2). YAML only configures the application itself (datasource, algorithm, thread pool, schedule). This is what lets a new client or a changed path take effect via a data change at runtime instead of a config/code change.

### 7.3 Seeding demo data

For the H2 demo phase, seed `client` and `client_file_location` via `data.sql` (Spring Boot's standard seeding mechanism) so the service is runnable out of the box with example clients/paths, without that seeding mechanism becoming the permanent configuration path.

### 7.4 H2 mode

Use file-based H2 (not in-memory) so discovered/completed records persist across restarts — this matters because the whole point of `dump_file_record` is to remember what's already been done.

## 8. Non-Goals for This Phase

- No Oracle (or other production) datasource — H2 only, per requirement 4.
- No multi-node/distributed coordination — the claim pattern in §6 is written to be safe under that scenario later, but running multiple app instances concurrently is not a target of this phase.
- No authentication/authorization on any exposed endpoint.
- No per-environment YAML profiles — one `application.yaml` only.
- No import statements in specs/snippets produced from this document — that's an implementation detail for whoever writes the actual `.java` files.

## 9. Open Assumptions to Confirm Before Implementation

- Checksum algorithm defaults to SHA-256; confirm this meets whatever downstream verification the checksums are used for.
- Dedup key is `(client_id, file_path)`; if a client's dump file can be legitimately replaced at the same path and should be re-checksummed, that requires an explicit "reprocess" action (e.g. comparing `file_size`/`last_modified` against the stored record and re-opening it), which is not built by default under requirement 3's "don't pick up again" rule.
- Virtual threads are the default executor; if profiling on real dump files shows digest computation (not I/O) is the bottleneck, switch `checksum.executor.type` to `fixed`.