# Runbook — Oracle Dump Checksum Service

An operational guide for the service specified in [`claude.md`](./claude.md) and technically analyzed in [`analysis.md`](./analysis.md). This document is written for **end users and operators**: how the system behaves, and the exact steps to build, run, verify, and operate it. Diagrams are pre-rendered PNGs stored in [`images/`](./images) with a light background — no diagram source is embedded inline in this document.

## Table of Contents

1. [What This Service Does](#1-what-this-service-does)
2. [Architecture Overview](#2-architecture-overview)
3. [Data Model](#3-data-model)
4. [The Never-Pick-Twice Guarantee](#4-the-never-pick-twice-guarantee)
5. [DumpFileRecord Lifecycle](#5-dumpfilerecord-lifecycle)
6. [One Scan Cycle, End to End](#6-one-scan-cycle-end-to-end)
7. [Prerequisites](#7-prerequisites)
8. [Configuration Reference](#8-configuration-reference)
9. [Step-by-Step: Running the Application](#9-step-by-step-running-the-application)
10. [Verifying the Demo Data](#10-verifying-the-demo-data)
11. [REST API Reference](#11-rest-api-reference)
12. [Operating the Service — Common Tasks](#12-operating-the-service--common-tasks)
13. [Stopping the Application](#13-stopping-the-application)
14. [Troubleshooting](#14-troubleshooting)
15. [Notes and Known Limitations](#15-notes-and-known-limitations)
16. [References](#16-references)

---

## 1. What This Service Does

The service scans one or more **client** directories for Oracle dump files (`*.dmp` by default), computes a **SHA-256 checksum** for every file it finds, and stores the result in an H2 database. Two properties make this more than a shell script wrapped in a scheduler:

- **It is fast.** Every file is checksummed concurrently on its own virtual thread — hundreds of files can be "in flight" (mostly waiting on disk I/O) without exhausting OS threads.
- **It never processes the same file twice.** Once a file has a database row — in any status, including `FAILED` — it is never re-discovered. Concurrent workers "claim" a file via a single atomic SQL `UPDATE`, so even if two threads race for the same file, exactly one of them wins.

**Note:** which directories belong to which client is **not** hardcoded. It lives in two H2 tables (`client`, `client_file_location`). Adding a client or changing a path is an `INSERT`/`UPDATE`, not a code change or redeploy — see [§12.1](#121-add-a-new-client-at-runtime).

## 2. Architecture Overview

![Architecture Overview](images/architecture-overview.png)
*High-level component view. Light background, rendered PNG — see [References](#16-references) for the diagramming tool. Arrows show call direction, not data direction.*

**How to read it:** `ChecksumScanScheduler` is the only class that runs on a timer (`@Scheduled`); it calls `FileDiscoveryService.discover()` and then `ChecksumComputationService.processDiscoveredFiles()` in sequence. `ChecksumStatusController` calls the same scheduler method for a manual, on-demand run, so "scheduled" and "triggered by a user" are the exact same code path — there is no separate manual-run implementation to keep in sync.

**Example — why the database, not the file system, is authoritative:** it is tempting to think "if the file is on disk, check whether we've hashed it." Instead, the *existence of a row* in `dump_file_record` for `(client_id, file_path)` is what "already known" means. This matters in practice: a dump file can later be archived or deleted, and the service must still refuse to reprocess a same-named replacement without an explicit decision (see [§15](#15-notes-and-known-limitations)).

## 3. Data Model

![Entity Relationship Diagram](images/er-diagram.png)
*Three tables. The unique constraint on `(client_id, file_path)` is the load-bearing part of the whole design — it is what makes "already picked up" a database fact instead of something held only in memory.*

| Table | Purpose |
|---|---|
| `client` | One row per client (`code`, `name`, `active`). Deactivating a client (`active = FALSE`) stops it being scanned without deleting its history. |
| `client_file_location` | One row per watched directory per client (`base_path`, `file_pattern`, `active`). A client can have multiple watched paths. |
| `dump_file_record` | One row per discovered file: size, last-modified time, checksum algorithm/value, status, and claim/completion timestamps. |

**Example — adding a client is data, not code:**
```sql
INSERT INTO client (code, name, active) VALUES ('INITECH', 'Initech LLC', TRUE);
INSERT INTO client_file_location (client_id, base_path, file_pattern, active)
  SELECT id, '/mnt/dumps/initech', '*.dmp', TRUE FROM client WHERE code = 'INITECH';
```
This takes effect on the very next scheduled scan — no restart required, because `ClientConfigService.activeLocations()` queries these tables fresh every cycle.

**Note:** the natural key for de-duplication is `(client_id, file_path)`, **not** `(client_id, base_path)`. Two files in the same watched directory (e.g. `sample1.dmp` and `sample2.dmp` for client `ACME`) each get their own independent row.

## 4. The Never-Pick-Twice Guarantee

![Claim Race Sequence](images/claim-race-sequence.png)
*Two virtual threads race to process the same file (row id 42). The database decides the winner — not a Java lock.*

The claim is a single conditional SQL statement, not a "check, then write":

```sql
UPDATE dump_file_record
SET status = 'CLAIMED', claimed_by = :claimedBy, claimed_at = :claimedAt
WHERE id = :id AND status = 'DISCOVERED'
```

**Why this matters:** a `synchronized` block or an in-memory `Set` of "files being processed" only protects threads inside **one** JVM. This `UPDATE ... WHERE` protects the row regardless of how many threads — or, later, how many application instances — are racing for it, because the guarantee comes from the database transaction, not from process memory. A worker trusts exactly one signal: how many rows the `UPDATE` affected.

- **1 row affected** → this worker won the claim. It proceeds to open the file and compute the checksum.
- **0 rows affected** → another worker already claimed it (the `WHERE status = 'DISCOVERED'` predicate is now false). This worker returns immediately without touching the file.

**Worked example (matches the diagram):** worker A and worker B both see file id `42` as a candidate. Both fire the `UPDATE`. H2 serializes the two statements at the row level: whichever commits first gets `1` row affected and goes on to hash the file; the second gets `0` rows affected and exits — it never opens the file at all. No file is ever read by two workers at once, and no explicit lock object appears anywhere in the code.

## 5. DumpFileRecord Lifecycle

![Status State Machine](images/status-state-machine.png)
*Four states. Three transitions. No path back to `DISCOVERED` in this phase — a deliberate simplification, not an oversight (see [§15](#15-notes-and-known-limitations)).*

| Status | Meaning |
|---|---|
| `DISCOVERED` | File found on disk, row inserted, not yet claimed by any worker. |
| `CLAIMED` | A worker's atomic `UPDATE` succeeded; that worker is now reading and hashing the file. |
| `COMPLETED` | Checksum computed and stored (`checksum_algorithm`, `checksum_value`, `completed_at`). |
| `FAILED` | The claiming worker hit an `IOException` or `NoSuchAlgorithmException` while hashing. |

**Note — the accepted gap:** if the process is killed (`kill -9`) while a file is `CLAIMED`, that row stays `CLAIMED` forever on restart. It will not be retried automatically (only `DISCOVERED` rows are claimed) and it will not be re-discovered either (discovery only checks whether *any* row exists for that path). Recovering it today requires a manual `UPDATE dump_file_record SET status = 'DISCOVERED' WHERE id = ...`. A periodic sweep to auto-reset stale claims is flagged in `claude.md` §6.5 as optional future hardening, not built in this phase.

## 6. One Scan Cycle, End to End

![Scan Cycle Sequence](images/scan-cycle-sequence.png)
*Everything that happens between one `@Scheduled` firing of `ChecksumScanScheduler.scan()` and the next.*

**Worked example, using the demo seed data (`data.sql`):**

1. **First scan after startup:** `discover()` lists `./demo-data/acme/*.dmp` → finds `sample1.dmp` and `sample2.dmp`, neither known yet → two `DISCOVERED` rows inserted. Same for `./demo-data/globex/sample1.dmp` → one more `DISCOVERED` row. Three candidates total.
2. `processDiscoveredFiles()` submits three tasks to the virtual-thread executor — one per candidate id.
3. Each task claims its own id (no contention in this example — one candidate thread per id), reads the file through a `FileChannel` in 8 MB chunks, computes SHA-256, and writes `status = COMPLETED` with the checksum value.
4. **Second scan cycle**, ~30 seconds later by default: `discover()` re-lists the same directories, but `existsByClient_IdAndFilePath` now returns `true` for all three paths — **zero** new rows are inserted, and `processDiscoveredFiles()` finds no `DISCOVERED` candidates, so it submits nothing.

This was verified against the running service in this environment: calling `POST /api/checksum/scan` after the records were already `COMPLETED` returned immediately and left the status summary unchanged at `{"COMPLETED": 3}` (see [§10](#10-verifying-the-demo-data) for the exact output captured).

## 7. Prerequisites

| Requirement | Notes |
|---|---|
| **Java 21+** (JDK) | The project targets Java 21 language level. Verify with `java -version`. |
| **Maven 3.9+** | No Maven Wrapper (`mvnw`) is checked into this repository — use a system-installed `mvn`. Verify with `mvn -v`. |
| **Network access for the first build** | Maven needs to download dependencies (Spring Boot 4.1.1, H2, Lombok, etc.) into `~/.m2/repository` on the first build. Subsequent builds can run with `mvn -o` (offline). |
| **A free TCP port** | Default `8080` for the embedded Tomcat server. |
| **~50 MB disk space** | For the Maven-built `target/` directory and the H2 database files under `data/`. |

**Note:** no Oracle client, Oracle Instant Client, or real Oracle database is required to run this service. It only reads dump files as opaque binary streams for hashing — it never opens or interprets an Oracle export format.

## 8. Configuration Reference

Everything below lives in the single [`src/main/resources/application.yaml`](src/main/resources/application.yaml) — there are no per-environment profile files in this phase.

| Key | Default | Meaning |
|---|---|---|
| `spring.datasource.url` | `jdbc:h2:file:./data/checksum-db;AUTO_SERVER=TRUE` | File-based H2 (not in-memory) — data survives restarts. `AUTO_SERVER=TRUE` lets a second process (e.g. the H2 console launched separately) connect concurrently. |
| `spring.jpa.hibernate.ddl-auto` | `update` | Hibernate creates/updates tables from the entity classes. Demo-only convenience — see [§15](#15-notes-and-known-limitations). |
| `spring.h2.console.enabled` | `true` | Enables the browser-based H2 console at `/h2-console` for demo inspection. |
| `spring.sql.init.mode` | `always` | Runs `data.sql` on every startup (it's idempotent — see [§8 note](#note-on-datasql) below). |
| `checksum.algorithm` | `SHA-256` | Digest algorithm passed to `MessageDigest.getInstance(...)`. Any algorithm name the JDK supports works (e.g. `SHA-512`, `MD5`), without a code change. |
| `checksum.executor.type` | `virtual` | `virtual` = one virtual thread per file (default, best when I/O-bound). `fixed` = a bounded platform-thread pool (better if digest computation, not disk I/O, turns out to be the bottleneck on real hardware). |
| `checksum.executor.fixed-pool-size` | `4` | Pool size used **only** when `checksum.executor.type: fixed`. |
| `checksum.scan.cron` | `0/30 * * * * *` | Standard Spring cron expression — how often `ChecksumScanScheduler.scan()` fires automatically. Default: every 30 seconds. |

<a id="note-on-datasql"></a>
**Note on `data.sql`:** the demo seed script (`src/main/resources/data.sql`) uses `INSERT ... SELECT ... WHERE NOT EXISTS` rather than plain `INSERT`, so it is safe to run on every restart without violating the unique constraint on `client.code`. It seeds two demo clients:

| Client code | Base path | Pattern |
|---|---|---|
| `ACME` | `./demo-data/acme` | `*.dmp` |
| `GLOBEX` | `./demo-data/globex` | `*.dmp` |

## 9. Step-by-Step: Running the Application

These steps were followed and verified in this environment (macOS, Java 25 runtime, Maven 3.9.9, Spring Boot 4.1.1) before being written down.

### Step 1 — Open a terminal in the project directory

```bash
cd oracle_dump_checksum
```

All commands below assume this is the current directory (the one containing `pom.xml`).

### Step 2 — Confirm prerequisites

```bash
java -version
mvn -v
```

You should see Java 21 or newer, and a Maven version line. If either command is not found, install a JDK 21+ distribution (e.g. Temurin) and Apache Maven before continuing.

### Step 3 — Build the project

```bash
mvn clean package -DskipTests
```

**Note:** `-DskipTests` is used here only because the checked-in test class (`ChecksumApplicationTests`) is a context-load smoke test with no fixtures of its own; skip it for a quick build, or drop the flag to run it. This produces `target/checksum-service-0.0.1-SNAPSHOT.jar`.

### Step 4 — Run the application

Two equivalent ways to start it — pick one:

```bash
# Option A — via the Spring Boot Maven plugin (good for development)
mvn spring-boot:run

# Option B — run the packaged jar directly (closer to how it would run in a deployed environment)
java -jar target/checksum-service-0.0.1-SNAPSHOT.jar
```

### Step 5 — Confirm successful startup

Watch the console log for a line similar to:

```
Tomcat started on port(s): 8080 (http) with context path '/'
Started ChecksumApplication in X.XXX seconds
```

**Example of what actually happened in this environment:**
```
INFO ... TomcatWebServer   : Tomcat initialized with port 8080 (http)
INFO ... HikariDataSource  : HikariPool-1 - Start completed.
INFO ... ChecksumApplication : Started ChecksumApplication in 4.9 seconds
```

At this point the scheduler is already running: within 30 seconds (the default `checksum.scan.cron`), the two demo clients' directories will be scanned automatically and their sample `.dmp` files checksummed — no manual action is required to see results.

### Step 6 — (Optional) Open the H2 console to inspect the database visually

1. Navigate a browser to `http://localhost:8080/h2-console`.
2. **JDBC URL:** `jdbc:h2:file:./data/checksum-db`
3. **User Name:** `sa`
4. **Password:** *(leave blank)*
5. Click **Connect**, then run `SELECT * FROM dump_file_record;` to see discovered/completed rows directly.

## 10. Verifying the Demo Data

With the app running, check the status summary endpoint:

```bash
curl -s http://localhost:8080/api/checksum/status
```

**Actual output captured from a running instance in this environment**, after the first automatic scan cycle completed:
```json
{"COMPLETED":3}
```

This means: 3 files were discovered across the two demo clients (`ACME`: `sample1.dmp`, `sample2.dmp`; `GLOBEX`: `sample1.dmp`), and all 3 were successfully checksummed.

Trigger a manual scan and confirm it is a no-op the second time (because the files are already known):

```bash
curl -s -X POST http://localhost:8080/api/checksum/scan
# {"status":"scan triggered"}

curl -s http://localhost:8080/api/checksum/status
# {"COMPLETED":3}   <- unchanged, exactly as expected per §6
```

## 11. REST API Reference

| Method & Path | Purpose |
|---|---|
| `POST /api/checksum/scan` | Manually triggers one discovery + checksum cycle immediately (same code path the scheduler uses). |
| `GET /api/checksum/status` | Returns a count of records grouped by status, e.g. `{"DISCOVERED":1,"COMPLETED":3}`. |
| `GET /api/checksum/records` | Returns the full list of `dump_file_record` rows as JSON, including file path, size, checksum, and timestamps. |

**Example — `GET /api/checksum/records` (real response, trimmed to one record, captured from this environment):**
```json
{
  "id": 1,
  "client": { "id": 1, "code": "ACME", "name": "Acme Corporation", "active": true },
  "filePath": "/abs/path/to/oracle_dump_checksum/demo-data/acme/sample2.dmp",
  "fileSize": 52,
  "lastModified": "2026-09-12T18:34:28.832519Z",
  "checksumAlgorithm": "SHA-256",
  "checksumValue": "9c9549395adc2fd0c74145eb3f48ae15b4b7fd151a29e73a407344fc6a7832ae",
  "status": "COMPLETED",
  "claimedBy": "VirtualThread[#63]/runnable@ForkJoinPool-1-worker-1",
  "claimedAt": "2026-09-13T23:02:31.060139Z",
  "completedAt": "2026-09-13T23:02:31.171260Z"
}
```

**Note:** `claimedBy` records the virtual thread that won the claim race (§4) — useful for diagnostics if you ever need to confirm no two records were claimed by the same worker at conflicting times, or when auditing behavior after a crash.

## 12. Operating the Service — Common Tasks

### 12.1 Add a new client at runtime

No restart needed. Either run SQL directly against the H2 console (§9 Step 6), or execute it via any SQL client pointed at `jdbc:h2:file:./data/checksum-db`:

```sql
INSERT INTO client (code, name, active) VALUES ('INITECH', 'Initech LLC', TRUE);
INSERT INTO client_file_location (client_id, base_path, file_pattern, active)
  SELECT id, '/mnt/dumps/initech', '*.dmp', TRUE FROM client WHERE code = 'INITECH';
```

Within one scan cycle (default 30 seconds, or immediately via `POST /api/checksum/scan`), any `.dmp` files under `/mnt/dumps/initech` will be discovered and checksummed.

### 12.2 Add a second watched path for an existing client

```sql
INSERT INTO client_file_location (client_id, base_path, file_pattern, active)
  SELECT id, '/mnt/dumps/acme/archive', '*.dmp', TRUE FROM client WHERE code = 'ACME';
```

### 12.3 Temporarily stop scanning a client without deleting its history

```sql
UPDATE client SET active = FALSE WHERE code = 'ACME';
```
Existing `dump_file_record` rows for `ACME` are untouched; only future discovery is skipped. Set `active = TRUE` again to resume.

### 12.4 Retire one watched path but keep the client active

```sql
UPDATE client_file_location SET active = FALSE WHERE base_path = '/mnt/dumps/acme/archive';
```

### 12.5 Change the checksum algorithm

Edit `checksum.algorithm` in `application.yaml` (e.g. to `SHA-512`) and restart. **Note:** this only affects files discovered *after* the change — existing `checksum_algorithm`/`checksum_value` values on already-completed rows are not recomputed.

### 12.6 Recover a file stuck in `CLAIMED` after a crash

As covered in [§5](#5-dumpfilerecord-lifecycle), this requires a manual reset (no automatic sweep exists yet in this phase):
```sql
UPDATE dump_file_record SET status = 'DISCOVERED', claimed_by = NULL, claimed_at = NULL
WHERE id = <id> AND status = 'CLAIMED';
```

## 13. Stopping the Application

- **`mvn spring-boot:run`**: press `Ctrl+C` in the terminal running it.
- **Packaged jar**: press `Ctrl+C`, or if running in the background, find and stop the process:
  ```bash
  jps -l | grep checksum-service      # find the PID
  kill <pid>                          # graceful shutdown (SIGTERM)
  ```
**Note:** the checksum executor bean is registered with `destroyMethod = "close"`, so a graceful shutdown (`SIGTERM`, i.e. plain `kill`, not `kill -9`) allows in-flight checksum tasks to finish rather than being abandoned mid-hash. Avoid `kill -9` unless testing the crash-recovery scenario in [§12.6](#126-recover-a-file-stuck-in-claimed-after-a-crash) on purpose.

## 14. Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Web server failed to start. Port 8080 was already in use.` | Another instance of the app (or something else) is already listening on 8080. | Stop the other process, or run with `--server.port=0` (random free port) / set `server.port` in `application.yaml`. |
| Startup fails during `data.sql` execution / "table not found" | `spring.jpa.defer-datasource-initialization` was removed or set to `false`. | Ensure it stays `true` in `application.yaml` so Hibernate creates tables (via `ddl-auto: update`) before `data.sql` runs. |
| A client's files never get discovered | The `client_file_location.base_path` does not exist, isn't a directory, or `active = FALSE`; or the client itself is `active = FALSE`. | Check with `SELECT * FROM client_file_location;` / `SELECT * FROM client;` via the H2 console; the log also prints a `WARN` line ("... not a directory") when a configured path is missing. |
| A file never reaches `COMPLETED` | It failed hashing (permissions, file moved mid-read) and is now `FAILED`; or it's stuck `CLAIMED` after a crash. | Check `GET /api/checksum/status` and `/records` for its current status; see [§12.6](#126-recover-a-file-stuck-in-claimed-after-a-crash) if `CLAIMED`. |
| Replacing a file's content at the same path doesn't get re-checksummed | This is expected — see [§15](#15-notes-and-known-limitations). The dedup key is the path alone. | No built-in fix in this phase; would need an explicit "reprocess" action. |
| H2 console shows "Database may be already in use" | Another process (or a leftover locked session) already has the file-based database open. | Ensure only one application instance points at the same `data/checksum-db` file at a time, or connect via the same `AUTO_SERVER=TRUE` URL rather than a second exclusive connection. |

## 15. Notes and Known Limitations

These are explicit, intentional simplifications for this demo phase (see `claude.md` §8–9), not oversights:

- **No stale-claim sweep.** A worker crash mid-hash leaves a row `CLAIMED` forever until manually reset ([§12.6](#126-recover-a-file-stuck-in-claimed-after-a-crash)).
- **No config caching.** Client/path configuration is read from H2 on every scan cycle — fine at demo scale; a TTL cache is a future option if the number of clients grows large.
- **Replacing a file at the same path is invisible to the system.** The dedup key is `(client_id, file_path)` alone; `file_size`/`last_modified` are captured but never compared against a later state of the same path.
- **Single-instance only, by design of this phase.** The claim `UPDATE` (§4) is *written* to be safe with multiple app instances against the same database, but running multiple instances has not been exercised — H2 file-mode is realistically single-instance.
- **No authentication on `/api/checksum/*`.** Acceptable for a local H2 demo; not appropriate to carry into any shared or production environment.
- **`ddl-auto: update` is a demo convenience**, not a migration strategy — a real deployment would move to a versioned migration tool (e.g. Flyway) as detailed in `analysis.md` §11.

## 16. References

- [`claude.md`](./claude.md) — the technical specification this service was implemented from.
- [`analysis.md`](./analysis.md) — a deeper design analysis, including a full H2-to-SQL-Server production migration walkthrough.
- Diagrams in [`images/`](./images) were authored as PlantUML source and rendered to PNG (`plantuml -tpng`) — only the rendered images are kept in this repository, per the "use an image, not diagram source" convention for this document.
- [Java `MessageDigest` — thread-safety note](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html) — the JDK documentation stating a fresh instance is required per thread.
- [`Executors.newVirtualThreadPerTaskExecutor()` — JDK 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor())
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) — background on why virtual threads suit I/O-bound workloads like file hashing.
- [Spring Data JPA — Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.modifying-queries) — the mechanism behind the atomic claim query.
- [H2 Database — Locking and transaction isolation](https://www.h2database.com/html/advanced.html#transaction_isolation) — why the conditional `UPDATE` claim is race-safe under H2's default isolation.
- [Spring Boot — Data Initialization (`data.sql`, `defer-datasource-initialization`)](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [H2 Database — Console](https://www.h2database.com/html/tutorial.html#console_application) — reference for the `/h2-console` UI used in [§9 Step 6](#step-6--optional-open-the-h2-console-to-inspect-the-database-visually).
- [Spring Boot Reference — `@Scheduled` and cron expressions](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-cron-expression) — syntax reference for `checksum.scan.cron`.
