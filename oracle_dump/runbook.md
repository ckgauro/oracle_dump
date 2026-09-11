# Runbook — Oracle Dump Importer

A step-by-step guide to **build, run, feed, verify and recover** the service. Every step has a
screenshot from a real run. Images live in [`images/`](images/).

> **Who this is for:** an operator or developer running the service locally or on a server. No
> knowledge of the internals is required. For the "how it works" version, see
> [`architecture.md`](architecture.md).

![Runbook at a glance](images/r00-workflow.png)
*The nine steps below, at a glance. Steps 1–7 are the normal path; 8–9 are recovery.*

---

## Contents

- [Step 0 — Prerequisites](#step-0--prerequisites)
- [Step 1 — Get the code](#step-1--get-the-code)
- [Step 2 — Build and run the tests](#step-2--build-and-run-the-tests)
- [Step 3 — Start the service](#step-3--start-the-service)
  - [Option A — local development (`dev`)](#option-a--local-development-use-this-for-your-first-run)
  - [Option B — run the built jar (default)](#option-b--run-the-built-jar-closer-to-production-optional)
  - [Option C — the `windows` profile](#option-c--the-windows-profile-explicit-switch-same-as-the-default)
- [Step 4 — Verify it is healthy](#step-4--verify-it-is-healthy)
- [Step 5 — Drop a dump file](#step-5--drop-a-dump-file)
- [Step 6 — Watch it get imported](#step-6--watch-it-get-imported)
- [Step 7 — Inspect the result and the import log](#step-7--inspect-the-result-and-the-import-log)
- [Step 8 — Duplicate files](#step-8--duplicate-files)
- [Step 9 — When an import fails, and how to retry](#step-9--when-an-import-fails-and-how-to-retry)
- [Step 10 — Stop the service gracefully](#step-10--stop-the-service-gracefully)
- [Inspecting the H2 metadata database](#inspecting-the-h2-metadata-database)
  - [What database am I connecting to?](#a--what-database-am-i-connecting-to)
  - [The tables](#b--the-tables)
  - [The `DUMP_FILE` columns in detail](#c--the-dump_file-columns-in-detail)
  - [Option 1 — the H2 web console (easiest)](#option-1--the-h2-web-console-easiest)
  - [Option 2 — the H2 command-line shell](#option-2--the-h2-command-line-shell)
  - [Option 3 — any external JDBC client (DBeaver, IntelliJ)](#option-3--any-external-jdbc-client-dbeaver-intellij)
  - [Option 4 — from your own backend / Java code](#option-4--from-your-own-backend--java-code)
  - [Useful queries](#useful-queries)
- [Running as a Windows Service](#running-as-a-windows-service)
- [Troubleshooting](#troubleshooting)
- [Quick command reference](#quick-command-reference)
- [References](#references)

---

## Step 0 — Prerequisites

| Requirement | Version | Check |
|---|---|---|
| JDK | **25** (Temurin/OpenJDK) | `java -version` |
| Maven | not required — use the wrapper `./mvnw` (`mvnw.cmd` on Windows) | `./mvnw -v` |
| Disk | space for the metadata DB (`./data`) and import logs (`./logs`) | — |
| Ports | **8080** free (REST + Actuator) | — |
| Oracle client | **only for `importer.mode: impdp`/`imp`** — not needed for the default `mock` mode | `impdp -help` |

> **NOTE:** The **default** (no-profile) and **`windows`** profiles both point `oracle-client` at
> `C:\Oracle\...`, which obviously doesn't exist on macOS/Linux. In `mock` mode that produces a
> harmless `WARN` at startup and nothing else — it is not an error. The **`dev`** profile instead
> points `oracle-client` at `/Users/chandragauro/temp/oracle/...` (this developer's Mac) — if
> you're on a different machine, edit the `dev` block in `application.yaml` to match your own path.

---

## Step 1 — Get the code

```bash
cd <your workspace>
# the Maven project is in the oracle_dump/ subfolder of the repo
cd oracle_dump/oracle_dump
ls    # you should see: pom.xml  src/  mvnw  application.yaml and application-windows.yaml
      # under src/main/resources
```

---

## Step 2 — Build and run the tests

**Goal of this step:** turn the source code into a runnable program (`.jar`) and prove it works by
running the automated tests. You do this once now, and again any time you change the code.

### 2.1 — Open a terminal in the right folder

You must be in the folder that contains `pom.xml` — the same folder from
[Step 1](#step-1--get-the-code) (`.../oracle_dump/oracle_dump`). Check first:

```bash
pwd     # shows where you are   (on Windows: cd)
ls      # you must see: pom.xml  mvnw  mvnw.cmd  src/   (on Windows: dir)
```

If you don't see `pom.xml`, `cd` into the correct folder before continuing.

### 2.2 — Run the build

Copy this line exactly and press Enter:

```bash
./mvnw clean verify
```

On **Windows** use the `.cmd` version instead:

```bat
mvnw.cmd clean verify
```

What the parts mean, so it isn't magic:

| Part | What it does |
|---|---|
| `./mvnw` | The **Maven wrapper** — a small script included in the repo. It auto-downloads the exact build tool version needed, so you do **not** have to install Maven yourself. `./` means "run the script in *this* folder". |
| `clean` | Deletes the previous build output (the `target/` folder) so you start fresh. |
| `verify` | Compiles the code, runs **all the tests**, and packages the `.jar`. If any test fails, the build stops here. |

### 2.3 — First run is slow — that's normal

The **first** time, Maven downloads all the libraries the project depends on. This needs an
internet connection and can take several minutes. You'll see many `Downloading...` /
`Downloaded...` lines. Later runs reuse the downloads and take well under a minute.

### 2.4 — What a successful run looks like

Near the bottom of the output you should see the test summary followed by `BUILD SUCCESS`:

```text
Tests run: 40, Failures: 0, Errors: 0, Skipped: 1
...
BUILD SUCCESS
```

`Skipped: 1` is **expected** on macOS/Linux — that one test only checks Windows-style network
paths, so it deliberately doesn't run here. `Failures: 0, Errors: 0` is what matters.

![mvnw clean verify](images/r01-build-verify.png)
*Expected tail: `Tests run: 40, Failures: 0, Errors: 0, Skipped: 1` then `BUILD SUCCESS`.*

### 2.5 — What you now have

A runnable program at:

```text
target/oracle_dump-0.0.1-SNAPSHOT.jar
```

This is the file [Step 3](#step-3--start-the-service) starts. You do not need to understand its
contents — just know the build produced it.

### 2.6 — If it fails

| You see | What it means | What to do |
|---|---|---|
| `BUILD FAILURE` with `Could not resolve dependencies` / errors mentioning `spring-boot-h2console` | Maven couldn't download libraries — usually no internet, or you passed `-o` (offline) | Connect to the internet and run `./mvnw clean verify` again **without** `-o` |
| `BUILD FAILURE` mentioning `release version 25` / `invalid target release` / `UnsupportedClassVersionError` | Wrong Java version — this project needs **JDK 25** | Run `java -version`; install JDK 25 (see [Step 0](#step-0--prerequisites)) and retry |
| `bash: ./mvnw: Permission denied` | The wrapper script isn't marked executable | Run `chmod +x mvnw` once, then retry |
| `BUILD FAILURE` with `Tests run: … Failures: 1` (or more) and a test class name | A genuine test failure | Re-read the named test's output; this is a real code/environment problem, not a setup quirk |

> **TIP:** To build without running the tests (faster, but skips the safety check), use
> `./mvnw clean package -DskipTests`. For a first run, prefer the full `verify` so you know the
> project is healthy.

---

## Step 3 — Start the service

**Goal of this step:** launch the program so it runs continuously in your terminal, watching for
dump files. It stays running until you stop it (Step 10). For your **first run, use Option A.**

> **Before you start:** make sure nothing else is already using **port 8080** on your machine, and
> keep this terminal open — the service runs in the foreground and logs to it.

### Option A — local development (use this for your first run)

Run this from the same folder as before (the one with `pom.xml`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On **Windows**:

```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

What the parts mean:

| Part | What it does |
|---|---|
| `./mvnw spring-boot:run` | Tells the Maven wrapper to compile (if needed) and **start the application**. |
| `-Dspring-boot.run.profiles=dev` | Starts it with the **`dev` profile** — a bundle of beginner-friendly settings (see below). Without this it uses the production defaults, which scan nothing until you write a config file. |

The `dev` profile deliberately makes things easy:

- an **in-memory** database — nothing is written to disk, every restart is clean;
- **fast timings** — you don't wait long to see a file get imported;
- the **mock importer** — it simulates an Oracle import instead of needing a real Oracle client;
- it **auto-creates** two folders for you to drop files into:

  ```text
  /Users/chandragauro/temp/oracle-dumps/client-a     (imports 1 file at a time)
  /Users/chandragauro/temp/oracle-dumps/client-b     (imports up to 2 files at a time)
  ```

> **NOTE:** These are **absolute, machine-specific paths** — the `dev` profile in this repo is
> configured for this developer's Mac. If you're running it on a different machine, edit the
> `dump-directory` values under the `dev` profile block in `application.yaml` to point somewhere
> that exists on your machine.

#### How to tell it started correctly

Watch the log it prints. You are looking for these lines (order may vary):

```text
Registered client 'client-a' ...
Active dump importer: MockDumpImporter (mode=MOCK)
Oracle dump pipeline started
Started OracleDumpApplication in <n> seconds
```

Once you see `Started OracleDumpApplication`, the service is up. It will now sit there and keep
logging every scan cycle — **that's normal, leave it running** and move to Step 4 in a *new*
terminal.

![Start with the dev profile](images/r02-start-dev.png)
*Look for `Registered client 'client-a' …`, `Active dump importer: MockDumpImporter (mode=MOCK)`,
`Oracle dump pipeline started`, and `Started OracleDumpApplication`.*

> **NOTE:** A `WARN` line about `impdp` being `UNUSABLE` / `UNUSABLE` Oracle client is **expected**
> in mock mode — the mock importer never calls Oracle, so a missing `impdp.exe` doesn't matter.
> It is not an error.

#### If it won't start

| You see | What it means | What to do |
|---|---|---|
| `Web server failed to start. Port 8080 was already in use.` | Another program (maybe an old run of this service) holds the port | Stop the other program, or start on another port: add `-Dspring-boot.run.arguments=--server.port=8081` (then use `8081` in later steps) |
| `Unknown lifecycle phase ".run.profiles=dev"` or the flag seems ignored | Your shell split the `-D...` argument | Wrap it in quotes: `./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"` |
| It starts but exits immediately with a compile error | Code doesn't compile | Fix [Step 2](#step-2--build-and-run-the-tests) first — it must reach `BUILD SUCCESS` |
| `command not found: ./mvnw` / `Permission denied` | Wrong folder, or script not executable | `cd` to the folder with `pom.xml`; run `chmod +x mvnw` once |

To stop the service at any time, press **Ctrl-C** in this terminal (full details in
[Step 10](#step-10--stop-the-service-gracefully)).

### Option B — run the built jar (closer to production, optional)

Once you're comfortable, you can run the `.jar` from [Step 2](#step-2--build-and-run-the-tests)
directly, the way a server would:

```bash
java -jar target/oracle_dump-0.0.1-SNAPSHOT.jar \
     --spring.config.additional-location=file:./config/application.yml
```

This uses the **default** profile, not `dev`: the database is a **file** on disk at
`./data/oracle-import` (it survives restarts) and the client list is **empty** (`clients: []`), so
**nothing is scanned** until you add client entries to a config file. See
[architecture.md §12](architecture.md#12-configuration-reference) for the config format. Stick with
Option A until you need this.

### Option C — the `windows` profile (explicit switch, same as the default)

`src/main/resources/application-windows.yaml` holds the same Windows `oracle-client` paths and
example clients (a UNC share and a local `D:` path, from `CLAUDE.md` §26) that the **default**
profile already uses. It exists so you can switch between `dev` (Mac) and `windows` explicitly by
name, instead of relying on "no profile = Windows":

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=windows
```

On macOS/Linux the example clients point at paths (`//nas01/...`, `D:/OracleDumps/ClientB`) that
don't exist, so nothing will actually be scanned — this profile is mainly useful for validating
config wiring before deploying to a real Windows Server, or as a starting point to edit with your
real UNC share / local drive paths.

---

## Step 4 — Verify it is healthy

**Goal of this step:** confirm from *outside* the program that it is running and ready, by asking
it two questions over HTTP. Leave the service from Step 3 running and do this in a **new terminal**.

### 4.1 — One-time: install the helper tools

The examples use two small command-line tools:

| Tool | What it's for | Install / check |
|---|---|---|
| `curl` | Sends an HTTP request from the terminal. Pre-installed on macOS, most Linux, and Windows 10+. | `curl --version` |
| `jq` | Pretty-prints and colours the JSON reply so it's readable. Optional. | `jq --version` — macOS: `brew install jq`, Debian/Ubuntu: `sudo apt install jq`, Windows: `winget install jqlang.jq` |

If you don't want to install `jq`, just drop the `| jq` part from every command — you'll get the
same JSON on one line.

### 4.2 — Ask the service how it's doing

```bash
curl -s localhost:8080/api/status         | jq
curl -s localhost:8080/actuator/health    | jq
```

What the parts mean:

| Part | Meaning |
|---|---|
| `curl -s` | Make an HTTP GET request; `-s` = "silent", hides the download progress bar. |
| `localhost:8080` | Your own machine, port 8080 — where the service from Step 3 is listening. (If you started it on a different port, use that here.) |
| `/api/status` | This app's own summary endpoint — counts per status, importer mode, whether it's accepting work. |
| `/actuator/health` | A standard Spring Boot endpoint reporting `UP` / `DOWN` for each subsystem. |
| `| jq` | Pipe the JSON reply through `jq` to format it. |

### 4.3 — What a healthy reply looks like

`/api/status` on a fresh start: every `DumpStatus` count is `0`, `importerMode` is `MOCK`, and
`acceptingWork` is `true`.

`/actuator/health`: top-level `"status": "UP"`, with a `dumpDirectories` section listing each
client directory.

![status and health](images/r03-status-health.png)
*`/api/status` shows every `DumpStatus` at `0` on a fresh start, `importerMode: MOCK`,
`acceptingWork: true`. `/actuator/health` is `UP`, with per-client `dumpDirectories` details.*

Health components you'll see:

| Health component | Meaning |
|---|---|
| `dumpDirectories` | `UP` = every enabled client's directory is readable. `OUT_OF_SERVICE` = at least one directory/share is unreachable right now (temporary; the scanner keeps retrying). |
| `oracleClient` | In `mock` mode this stays `UP` even if `impdp.exe` is missing (just informational). In a real import mode a bad path makes it `OUT_OF_SERVICE`. |

### 4.4 — If the check fails

| You see | What it means | What to do |
|---|---|---|
| `curl: (7) Failed to connect to localhost port 8080` | The service isn't running (or is still starting, or is on another port) | Check the Step 3 terminal for `Started OracleDumpApplication`; wait a few seconds and retry |
| `curl: command not found` | `curl` isn't installed | Install it, or open the two URLs in a web browser instead |
| JSON prints but `jq: command not found` follows | `jq` isn't installed | Remove `| jq` from the command, or install `jq` (see 4.1) |
| `/actuator/health` shows `"status": "OUT_OF_SERVICE"` | A client dump directory isn't readable | In `dev` mode the folders are auto-created — check the Step 3 log for errors; otherwise fix the path/share and it recovers on its own |

---

## Step 5 — Drop a dump file

**Goal of this step:** put a file into one of the watched folders so the service picks it up. In
real life this file is an Oracle export; for this walkthrough any file will do. Keep the service
running; use your **second terminal**.

### 5.1 — The golden rule: never let the scanner see a half-written file

The service scans the folder on a timer. If it catches a file **mid-copy**, it could try to import
something incomplete. Two safeguards prevent that:

1. **The scanner ignores** files ending in `.part`, `.tmp`, `.partial`, `.copying`.
2. So the safe pattern is: **write to `name.dmp.part`, then rename to `name.dmp`** once the write
   is finished. A rename is instant, so the scanner only ever sees a complete file.

### 5.2 — Create a test file (macOS / Linux, `dev` profile)

```bash
# 1. write a ~7 KB file with a temporary name the scanner ignores
printf 'PAYLOAD%.0s' $(seq 1 1000) \
  > /Users/chandragauro/temp/oracle-dumps/client-a/clienta_2026_09.dmp.part

# 2. rename it into place — now the scanner will pick it up
mv /Users/chandragauro/temp/oracle-dumps/client-a/clienta_2026_09.dmp.part \
   /Users/chandragauro/temp/oracle-dumps/client-a/clienta_2026_09.dmp
```

- `printf 'PAYLOAD%.0s' $(seq 1 1000)` just prints the word `PAYLOAD` 1000 times — a quick way to
  make a non-empty file. The content is irrelevant.
- `>` writes that output to the file.
- `mv old new` renames it.

> If you edited the `dev` profile's `dump-directory` paths to somewhere else on your machine (see
> the note in [Step 3](#step-3--start-the-service)), substitute that path here instead.

### 5.3 — Create a test file (Windows, `windows` profile)

The `windows` profile (Option C, Step 3) ships with placeholder example paths (a UNC share and a
`D:` drive) that won't exist on a fresh machine. Edit `application-windows.yaml`'s `clients` block
to a real local folder first, then drop a file the same way:

```powershell
"PAYLOAD" * 1000 > D:\OracleDumps\ClientB\clientb_2026_09.dmp.part
Rename-Item D:\OracleDumps\ClientB\clientb_2026_09.dmp.part clientb_2026_09.dmp
```

You can also just **copy any existing file** into the configured folder and rename its extension
to `.dmp` — the mechanism is the same.

### 5.4 — Which folder?

The `dev` profile (Step 3, Option A) auto-creates two folders under
`/Users/chandragauro/temp/oracle-dumps/` — `client-a` and `client-b`. Use either. The filename
doesn't matter, but it must end in `.dmp`.

> **NOTE — how this looks with real uploads.** Point your export/backup job at
> `\\nas01\oracle-dumps\client-a\<name>.dmp.part` and have it rename to `.dmp` when the copy
> completes. If you can't control the upstream tool and it writes straight to `.dmp`, that still
> works — the scanner waits for several unchanged scans (`stable-scans-required`) **and** a quiet
> period (`stable-file-check-delay`) before touching the file, so you'll see it sit in
> `STABILIZING` for a few scan cycles first. That's expected, not a hang.

---

## Step 6 — Watch it get imported

**Goal of this step:** watch the file you just dropped move through the pipeline to `IMPORTED`.
You don't do anything here except observe — the service does the work automatically.

### 6.1 — Two ways to watch

- **The server log** — look at the terminal running the service from Step 3. It prints a line for
  each stage.
- **The status endpoint** — from your second terminal, run this every few seconds:

  ```bash
  curl -s localhost:8080/api/status | jq
  ```

  On macOS/Linux you can auto-repeat it: `watch -n 2 'curl -s localhost:8080/api/status | jq'`
  (press Ctrl-C to stop watching — this does **not** stop the service).

### 6.2 — The stages you'll see

The file's status moves through:

```text
STABILIZING → PENDING_IMPORT → (QUEUED → CHECKSUMMING →) IMPORTING → IMPORTED
```

| Stage | What's happening |
|---|---|
| `STABILIZING` | The scanner has seen the file but is waiting to be sure it's fully written (size + timestamp unchanged across scans). |
| `PENDING_IMPORT` | The file is confirmed stable and queued for a worker. |
| `QUEUED` / `CHECKSUMMING` | A worker picked it up and is computing its SHA-256 (used for duplicate detection). |
| `IMPORTING` | The import itself is running. In `dev`/mock mode this is a 3-second simulation. |
| `IMPORTED` | Done. Success. |

### 6.3 — How long it takes (dev profile)

Roughly **10–20 seconds** in `STABILIZING`, then the import starts within one poll cycle (5 s), and
the mock import runs for 3 s. So about half a minute end to end. A tiny file can still take the
full `STABILIZING` wait — that timing is deliberate, not a function of size.

![drop → scan → import](images/r04-drop-and-import.png)
*Top: the drop commands. Middle: `/api/status` over ~20 s. Bottom: the matching server log —
`Discovered dump` → `Dump eligible for import` → `Dispatched 1 dump(s)` → `Checksum SHA-256 …` →
`[mock] imported …` → `Imported record 1 …`.*

> **If nothing happens after a minute:** check the filename really ends in `.dmp` (not `.dmp.part`),
> that it's in `/Users/chandragauro/temp/oracle-dumps/client-a` or `.../client-b`, and that the scanner is enabled in
> the Step 3 log. See [Troubleshooting](#troubleshooting).

---

## Step 7 — Inspect the result and the import log

**Goal of this step:** look at what the service recorded about the import — the database row and
the detailed log file it wrote.

### 7.1 — List the imported files

```bash
curl -s 'localhost:8080/api/dumps?limit=20' | jq
```

- `/api/dumps` returns one JSON object per file the service knows about.
- `?limit=20` caps the list at 20 rows. You can also filter, e.g. `?status=IMPORTED` or
  `?status=FAILED`.

Each row includes: `status`, `sizeBytes`, `sha256` (the content fingerprint), `attemptCount`
(how many import tries), `importDurationMs`, and `importLogPath` (where the detailed log is on
disk).

![GET /api/dumps](images/r05-dumps.png)
*Each row: `status`, `sizeBytes`, the `sha256`, `attemptCount`, `importDurationMs`, and
`importLogPath`.*

### 7.2 — Open the detailed import log

The database stores only the **path** to the log; the full text is a file on disk. This one-liner
grabs the path of the most recent row and prints that file:

```bash
cat "$(curl -s localhost:8080/api/dumps | jq -r '.[0].importLogPath')"
```

How it works: the inner `curl ... | jq -r '.[0].importLogPath'` prints just the path string; `$( )`
substitutes it into the `cat` command. On **Windows PowerShell**:

```powershell
Get-Content (curl -s localhost:8080/api/dumps | ConvertFrom-Json)[0].importLogPath
```

![the import log file](images/r06-import-log.png)
*The mock importer writes a realistic transcript: `REMAP_SCHEMA=APP:CLIENT_A`,
`DIRECTORY=CLIENT_A_IMPORT_DIR`, `Bytes processed`, `successfully completed`. A real `impdp` run
will stream its stdout/stderr to this same file.*

Log files are laid out as `logs/imports/<client>/<yyyy>/<MM>/<uuid>.log`, so you can also find them
by hand under the `logs/` folder.

> **NOTE:** The original `.dmp` file is **left exactly where it was**. The service never deletes or
> moves dump files — it only records `IMPORTED` in its metadata database.

---

## Step 8 — Duplicate files

**Goal of this step:** see what happens when the *same file content* arrives twice. This is a
safety feature — it stops the same export being imported into Oracle twice.

### 8.1 — The rule

A file is a duplicate if another file with the **same content** was already seen **for the same
client** — regardless of its filename. "Same content" means the same SHA-256 checksum. Duplicates
are marked `DUPLICATE` and skipped: no import runs, no log file is written.

### 8.2 — Try it

```bash
# first file — let it finish importing
printf 'PAYLOAD-ALPHA%.0s' $(seq 1 300) > /Users/chandragauro/temp/oracle-dumps/client-a/dupe_first.dmp

#   ... watch /api/status until dupe_first.dmp reaches IMPORTED ...

# second file — different name, identical content
printf 'PAYLOAD-ALPHA%.0s' $(seq 1 300) > /Users/chandragauro/temp/oracle-dumps/client-a/dupe_second.dmp
```

Note these use `>` directly (no `.part` rename) just to keep the example short — the `.part`
convention from Step 5 is still the right habit for real files.

### 8.3 — What you'll see

`dupe_second.dmp` goes `STABILIZING → CHECKSUMMING`, then — because its checksum matches
`dupe_first.dmp` for `client-a` — it short-circuits straight to `DUPLICATE` instead of `IMPORTING`.

```bash
curl -s 'localhost:8080/api/dumps?status=DUPLICATE' | jq
```

![content de-duplication](images/r07-duplicate.png)
*`dupe_second.dmp` matches `dupe_first.dmp` on `(clientId + sha256)` during the checksum step and
short-circuits to `DUPLICATE` — no `impdp` run, no log file.*

> **NOTE:** The same content dropped for a *different* client (e.g. `client-b`) is **not** a
> duplicate — it will import normally. De-duplication is per client.

---

## Step 9 — When an import fails, and how to retry

**Goal of this step:** see the failure path and learn the one command that puts a failed file back
in the queue.

### 9.1 — What "failed" means

An import can fail for real reasons: an Oracle error, an IO error, or — if you switch to a real
import mode without an Oracle client installed — the "not implemented yet" `TODO`. The service
tells failures apart:

- **Retryable** failure → it waits (`retry-backoff`) and tries again, up to `max-attempts` times.
- After the last attempt → the row is parked in **`FAILED`** and left alone.

### 9.2 — Deliberately cause a failure

Stop the service (Ctrl-C), then restart it in a mode that has no working Oracle client, with a low
attempt count so you don't wait long:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--oracle-import.importer.mode=impdp \
     --oracle-import.processing.max-attempts=2 --oracle-import.processing.retry-backoff=3s"
```

Then drop a file:

```bash
printf 'DATA%.0s' $(seq 1 1200) > /Users/chandragauro/temp/oracle-dumps/client-a/needs_oracle.dmp
```

### 9.3 — What you'll see

The status goes `PENDING_IMPORT (attempt 0)` → `PENDING_IMPORT (attempt 1)` (first try failed,
backing off) → `FAILED (attempt 2)`.

Look at why it failed:

```bash
curl -s 'localhost:8080/api/dumps?status=FAILED' | jq '.[0] | {status,attemptCount,lastError}'
```

`lastError` holds the message — here, that the real `impdp` importer isn't implemented.

### 9.4 — Retry it

After you've fixed the underlying cause, re-queue the row by its `id` (the `id` field in the JSON;
`1` in this example):

```bash
curl -s -X POST localhost:8080/api/dumps/1/retry | jq
```

This resets it to `PENDING_IMPORT` with `attemptCount` back to `0`.

![failure path + retry](images/r08-failed-retry.png)
*`PENDING_IMPORT (0)` → `PENDING_IMPORT (1)` (attempt 1 failed, backing off) → `FAILED (2)`.
`GET /api/dumps?status=FAILED` shows `lastError`. After fixing the root cause,
`POST /api/dumps/1/retry` resets it to `PENDING_IMPORT` with `attemptCount = 0`.*

> **NOTE — fix the cause first.** `retry` only re-queues the row; it doesn't fix anything. In this
> example it will just fail again until a real `impdp.exe` is reachable — so switch back to
> `mode: mock` (restart without the extra arguments) to see it succeed. `retry` only works on
> `FAILED` and `MISSING` rows; anything else returns HTTP `409`.

> **NOTE — crash recovery is automatic.** If a worker or the whole service dies in the middle of an
> import, that row is left half-done and is automatically reset to `PENDING_IMPORT` — after
> `stale-processing-timeout`, and always on the next startup. You do **not** need to retry those by
> hand.

---

## Step 10 — Stop the service gracefully

**Goal of this step:** shut the service down cleanly so no import is left in a broken state.

### 10.1 — How to stop it

Go to the terminal running the service (the one from Step 3) and press **Ctrl-C** once.

- If it's running as a Windows Service instead, use `sc stop oracle-dump-importer` or your service
  wrapper's stop command.
- Press Ctrl-C **once** and wait — pressing it repeatedly can force-kill the process before it
  finishes cleaning up.

### 10.2 — What a clean shutdown looks like

In the log you'll see, in order:

```text
pipeline stopping; no new work will be accepted
... (in-flight imports finish, up to shutdown-grace-period — default 2 minutes) ...
All in-flight imports completed cleanly
... (Tomcat and the database connection close) ...
```

Then the prompt returns — the service has exited.

![graceful shutdown](images/r09-shutdown.png)
*`pipeline stopping; no new work will be accepted` → in-flight imports are given up to
`shutdown-grace-period` (default 2 min) to finish → `All in-flight imports completed cleanly` →
Tomcat and the datasource close.*

> **NOTE:** If an import is still running when the 2-minute grace period runs out, it is **not**
> corrupted. Its row is simply reclaimed to `PENDING_IMPORT` the next time you start the service
> and re-run. Nothing is lost, only delayed.

> **NOTE:** In the `dev` profile the database is in-memory, so stopping the service **erases all
> the run history** (`/api/dumps` starts empty next time). That's expected for `dev`. Option B and
> production use an on-disk database that survives restarts.

---

## Inspecting the H2 metadata database

**Goal of this section:** open the database the service uses for its bookkeeping, see which tables
exist, and run SQL against them — either with a GUI or from your own backend code.

> The REST API (`/api/status`, `/api/dumps`) already exposes everything the service records, and is
> the **recommended** way to read state. Go to the database directly when you want ad-hoc SQL,
> bulk exports, or to debug a stuck row.

### A — What database am I connecting to?

The service uses **H2**, an embedded Java SQL database. Which H2 you get depends on the profile:

| Profile | JDBC URL (from `application*.yaml`) | Where it lives | Reachable from outside the app? |
|---|---|---|---|
| **default** (jar / Option B) | `jdbc:h2:file:./data/oracle-import;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE` | a file on disk: `./data/oracle-import.mv.db` (relative to the working directory) | **Yes** — `AUTO_SERVER=TRUE` lets other processes connect to the same file while the app runs |
| **`dev`** | `jdbc:h2:mem:oracle-import;DB_CLOSE_DELAY=-1` | RAM only, inside the running JVM | **No** — only the built-in web console (same JVM) can see it; it is **gone** when the service stops |

Credentials for both: user **`sa`**, password **empty**.

Other facts worth knowing:

- **Schema management is automatic.** Hibernate creates/updates the schema on startup —
  `ddl-auto: update` (default profile) or `create-drop` (`dev`). There is no `schema.sql`.
- **Identifiers are UPPER-CASE.** H2 folds unquoted names, so the table is `DUMP_FILE` and columns
  are `CLIENT_ID`, `IMPORT_LOG_PATH`, etc. Quote them only if you write them lower-case:
  `"dump_file"` would **not** match.
- **Timestamps are stored in UTC** (`hibernate.jdbc.time_zone: UTC`).
- H2 version is **2.4.240** (Spring Boot 4.1.1) — matters only if you download a standalone jar
  (see Option 2); use the same major version.

### B — The tables

The application defines exactly **one** table. Everything else you see belongs to H2 itself.

| Table | Origin | Contents |
|---|---|---|
| `DUMP_FILE` | JPA entity `DumpFileRecord` | One row per dump file the service has ever seen, with its full lifecycle, checksum, import result and last error. This is the **single source of truth**. |
| `INFORMATION_SCHEMA.*` | built into H2 | Catalog views — `TABLES`, `COLUMNS`, `INDEXES`, `CONSTRAINTS`, `SETTINGS`. Read-only; use them to explore. |

There is **no** separate id-sequence table (the primary key is an H2 `IDENTITY` column) and no
Flyway/Liquibase history table (migrations are not used yet).

Indexes on `DUMP_FILE` (created from the entity's annotations):

| Name | Columns | Why it exists |
|---|---|---|
| `UK_DUMP_CLIENT_PATH` | `CLIENT_ID, ABSOLUTE_PATH` (unique) | one row per physical file per client |
| `IX_DUMP_STATUS_ELIGIBLE` | `STATUS, NEXT_ELIGIBLE_AT` | the dispatcher's "what can I run now?" query |
| `IX_DUMP_CLIENT_CHECKSUM` | `CLIENT_ID, SHA256` | duplicate detection |
| `IX_DUMP_IN_FLIGHT` | `STATUS, CLAIMED_AT` | the stale-record reaper |

### C — The `DUMP_FILE` columns in detail

| Column | Type (H2) | Null? | Meaning |
|---|---|---|---|
| `ID` | `BIGINT` identity | no | Primary key. This is the `id` you pass to `POST /api/dumps/{id}/retry`. |
| `VERSION` | `BIGINT` | no | JPA optimistic-lock counter. Bumped on every update; don't touch it by hand. |
| `CLIENT_ID` | `VARCHAR(128)` | no | Which configured client the file belongs to (`client-a`, …). |
| `FILE_NAME` | `VARCHAR(512)` | no | The file's name only (no directory). |
| `ABSOLUTE_PATH` | `VARCHAR(2048)` | no | Full normalised path (UNC-aware). Unique per client. |
| `SIZE_BYTES` | `BIGINT` | no | File size at the last scan. |
| `LAST_MODIFIED_EPOCH_MS` | `BIGINT` | no | File mtime (epoch millis). With `SIZE_BYTES`, this is the key that decides whether a stored checksum can be reused. |
| `SHA256` | `VARCHAR(64)` | yes | Verified content hash. `NULL` until the checksum stage runs. |
| `STATUS` | `VARCHAR(32)` | no | `DumpStatus` enum as text — see the list below. |
| `STABLE_SCAN_COUNT` | `INTEGER` | no | Consecutive scans where size+mtime were unchanged. |
| `FIRST_SEEN_AT` | `TIMESTAMP` | no | When the scanner first discovered the file (UTC). |
| `LAST_SEEN_AT` | `TIMESTAMP` | no | Most recent scan that found the file (UTC). |
| `PRESENT_ON_DISK` | `BOOLEAN` | no | `TRUE` if the last scan found the file; `FALSE` means it vanished. |
| `NEXT_ELIGIBLE_AT` | `TIMESTAMP` | no | Earliest time the dispatcher may pick this row up (retry backoff pushes it into the future). |
| `ATTEMPT_COUNT` | `INTEGER` | no | Import attempts so far. `retry` resets it to 0. |
| `WORKER_ID` | `VARCHAR(128)` | yes | Which worker currently owns the row; `NULL` when not claimed. |
| `CLAIMED_AT` | `TIMESTAMP` | yes | When the current worker claimed it. Used by the stale-record reaper. |
| `IMPORT_STARTED_AT` | `TIMESTAMP` | yes | When the import began. |
| `IMPORT_FINISHED_AT` | `TIMESTAMP` | yes | When it finished (success or failure). |
| `IMPORT_DURATION_MS` | `BIGINT` | yes | Wall-clock import time in milliseconds. |
| `IMPORT_LOG_PATH` | `VARCHAR(2048)` | yes | Path to the detailed per-import log file on disk. The log **content** is never stored in the DB (`CLAUDE.md` §22). |
| `DUPLICATE_OF_ID` | `BIGINT` | yes | When `STATUS = DUPLICATE`, the `ID` of the row whose content this duplicates. |
| `LAST_ERROR` | `CLOB` | yes | Error text from the most recent failure (truncated ~8 000 chars). |
| `LAST_ERROR_AT` | `TIMESTAMP` | yes | When that error was recorded. |

`STATUS` is always one of:
`DISCOVERED`, `STABILIZING`, `PENDING_IMPORT`, `QUEUED`, `CHECKSUMMING`, `IMPORTING`, `IMPORTED`,
`DUPLICATE`, `FAILED`, `MISSING`.

### Option 1 — the H2 web console (easiest)

Works for **both** profiles because it runs *inside* the service.

1. Start the service (any profile).
2. Open **`http://localhost:8080/h2-console`** in a browser.
3. On the login screen enter:

   | Field | default profile | `dev` profile |
   |---|---|---|
   | JDBC URL | `jdbc:h2:file:./data/oracle-import` | `jdbc:h2:mem:oracle-import` |
   | User Name | `sa` | `sa` |
   | Password | *(leave blank)* | *(leave blank)* |

   The URL **must match the running app's URL** (the console connects as a peer). If you get
   `Database may be already in use`, you typed a `file:` URL without `AUTO_SERVER` while the app
   holds the file — add `;AUTO_SERVER=TRUE` or use the exact URL from the table in section A.
4. Click **Connect**, then run SQL, e.g. `SELECT * FROM DUMP_FILE;`.

> The console is enabled by `spring.h2.console.enabled: true` in `application.yaml`. **Turn it off
> in production** (`architecture.md` §"Security notes").

### Option 2 — the H2 command-line shell

Good for scripting against the **file** DB (default profile). Use the H2 jar Maven already
downloaded:

```bash
java -cp ~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar org.h2.tools.Shell \
  -url "jdbc:h2:file:./data/oracle-import;AUTO_SERVER=TRUE" -user sa -password ""
```

On **Windows** the jar is under `%USERPROFILE%\.m2\repository\...`. At the `sql>` prompt:

```sql
SHOW TABLES;
SELECT ID, CLIENT_ID, FILE_NAME, STATUS, ATTEMPT_COUNT FROM DUMP_FILE ORDER BY ID;
```

Non-interactive (one query, then exit): add `-sql "SELECT COUNT(*) FROM DUMP_FILE"`.

The in-memory `dev` DB **cannot** be reached this way — it exists only in the service's JVM.

### Option 3 — any external JDBC client (DBeaver, IntelliJ)

For the **file** DB while the service is running, `AUTO_SERVER=TRUE` allows a second connection:

- **Driver:** H2 (`org.h2.Driver`), version 2.4.x
- **JDBC URL:** `jdbc:h2:file:/ABSOLUTE/PATH/TO/data/oracle-import;AUTO_SERVER=TRUE`
  (use the real absolute path to the `.mv.db` file, without the `.mv.db` suffix)
- **User:** `sa`  **Password:** *(blank)*

If the service is **not** running, connect with a plain `jdbc:h2:file:/ABSOLUTE/PATH/...` URL (no
`AUTO_SERVER` needed).

### Option 4 — from your own backend / Java code

Inside the Spring application you already have first-class access — don't open a second JDBC
connection.

**a. Use the existing repository.** `DumpFileRepository` (a Spring Data JPA
`JpaRepository<DumpFileRecord, Long>`) is a bean you can inject anywhere:

```java
@Service
class ReportingService {

    private final DumpFileRepository dumps;

    ReportingService(DumpFileRepository dumps) {
        this.dumps = dumps;
    }

    long imported() {
        return dumps.countByStatus(DumpStatus.IMPORTED);
    }

    List<DumpFileRecord> recentFailures() {
        return dumps.findByStatusOrderByLastSeenAtDesc(DumpStatus.FAILED, Limit.of(20));
    }
}
```

Methods that already exist: `findByClientId`, `findByClientIdAndAbsolutePath`, `countByStatus`,
`findByStatusOrderByLastSeenAtDesc`, plus the pipeline's `findClaimable` / `claim` /
`reclaimStale` / `findChecksumSiblings`.

**b. Add your own query** to that interface — JPQL against the entity, or native SQL:

```java
public interface DumpFileRepository extends JpaRepository<DumpFileRecord, Long> {

    // JPQL — entity/field names
    @Query("select r from DumpFileRecord r where r.clientId = :c and r.status = :s")
    List<DumpFileRecord> byClientAndStatus(@Param("c") String clientId,
                                           @Param("s") DumpStatus status);

    // native SQL — real table/column names
    @Query(value = "SELECT STATUS, COUNT(*) FROM DUMP_FILE GROUP BY STATUS",
           nativeQuery = true)
    List<Object[]> statusHistogram();
}
```

**c. Drop to `JdbcTemplate`** for one-off SQL without an entity:

```java
@Component
class DumpStats {
    private final JdbcTemplate jdbc;
    DumpStats(JdbcTemplate jdbc) { this.jdbc = jdbc; }   // auto-configured from the datasource

    Map<String, Long> countByClient() {
        return jdbc.query(
            "SELECT CLIENT_ID, COUNT(*) c FROM DUMP_FILE GROUP BY CLIENT_ID",
            rs -> {
                Map<String, Long> out = new LinkedHashMap<>();
                while (rs.next()) out.put(rs.getString("CLIENT_ID"), rs.getLong("c"));
                return out;
            });
    }
}
```

> Treat writes with care: the pipeline relies on the `STATUS` state machine and the `VERSION`
> optimistic lock. Prefer the REST `retry` endpoint over hand-updating `STATUS`. Read-only queries
> are always safe.

### Useful queries

```sql
-- everything, newest first
SELECT ID, CLIENT_ID, FILE_NAME, STATUS, SIZE_BYTES, ATTEMPT_COUNT, IMPORT_DURATION_MS
FROM DUMP_FILE
ORDER BY ID DESC;

-- how many files in each state
SELECT STATUS, COUNT(*) FROM DUMP_FILE GROUP BY STATUS ORDER BY 2 DESC;

-- failures with the reason
SELECT ID, CLIENT_ID, FILE_NAME, ATTEMPT_COUNT, LAST_ERROR_AT, LAST_ERROR
FROM DUMP_FILE
WHERE STATUS = 'FAILED'
ORDER BY LAST_ERROR_AT DESC;

-- rows currently claimed by a worker (in flight)
SELECT ID, CLIENT_ID, FILE_NAME, STATUS, WORKER_ID, CLAIMED_AT
FROM DUMP_FILE
WHERE STATUS IN ('QUEUED', 'CHECKSUMMING', 'IMPORTING');

-- duplicates and what they duplicate
SELECT d.ID, d.FILE_NAME AS duplicate, o.FILE_NAME AS original, d.SHA256
FROM DUMP_FILE d JOIN DUMP_FILE o ON o.ID = d.DUPLICATE_OF_ID
WHERE d.STATUS = 'DUPLICATE';

-- stuck in STABILIZING for over an hour (UTC clock)
SELECT ID, CLIENT_ID, FILE_NAME, STABLE_SCAN_COUNT, FIRST_SEEN_AT, LAST_SEEN_AT
FROM DUMP_FILE
WHERE STATUS = 'STABILIZING'
  AND FIRST_SEEN_AT < DATEADD('HOUR', -1, CURRENT_TIMESTAMP());

-- inspect the schema itself
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'DUMP_FILE'
ORDER BY ORDINAL_POSITION;
```

---

## Running as a Windows Service

The application does not depend on any particular wrapper — use **WinSW**, **NSSM**, or the
**Java Service Wrapper**.

1. **Layout** (recommended, `CLAUDE.md` §21):

   ```text
   D:\OracleImporter\
     app\oracle-dump-importer.jar
     config\application.yml
     data\           (H2 metadata — LOCAL disk, never a share)
     logs\
     temp\
   ```

2. **Service account** — a dedicated domain account (`DOMAIN\oracle-import-svc`), **not**
   `LocalSystem`, granted: read the UNC share, read + execute the Oracle client bin, write `logs\`
   and `data\`, access `%TEMP%`.

3. **Example WinSW `oracle-dump-importer.xml`:**

   ```xml
   <service>
     <id>oracle-dump-importer</id>
     <name>Oracle Dump Importer</name>
     <executable>java</executable>
     <arguments>-jar "D:\OracleImporter\app\oracle-dump-importer.jar"
       --spring.config.additional-location=file:D:/OracleImporter/config/application.yml</arguments>
     <logpath>D:\OracleImporter\logs</logpath>
     <onfailure action="restart" delay="10 sec"/>
     <stoptimeout>150 sec</stoptimeout>   <!-- >= shutdown-grace-period -->
   </service>
   ```

4. **Config essentials** for production (`application.yml`):

   ```yaml
   spring:
     datasource:
       url: jdbc:h2:file:D:/OracleImporter/data/oracle-import;AUTO_SERVER=TRUE
     h2:
       console:
         enabled: false
   oracle-import:
     importer:
       mode: mock          # switch to impdp only once the Oracle client + DIRECTORY are ready
       import-log-root: "D:/OracleImporter/logs/imports"
     oracle-client:
       impdp-path: "C:/Oracle/product/19c/client_1/bin/impdp.exe"
       oracle-home: "C:/Oracle/product/19c/client_1"
       tns-admin:  "C:/Oracle/network/admin"
     clients:
       - client-id: client-a
         dump-directory: "//nas01/oracle-dumps/client-a"   # UNC, NOT a mapped drive
         target-schema: CLIENT_A
         oracle-directory-name: CLIENT_A_IMPORT_DIR
         max-parallel-imports: 1
   ```

   This is the same shape as `src/main/resources/application-windows.yaml` (see
   [Step 3, Option C](#step-3--start-the-service)) — that file is a convenient starting point:
   copy its `oracle-import` block into `application.yml` and replace the example paths with your
   real UNC share / Oracle client install.

5. Install & start:

   ```bat
   winsw install oracle-dump-importer.xml
   winsw start   oracle-dump-importer.xml
   sc query oracle-dump-importer
   ```

> **NOTE — mapped drives don't work for services.** A Windows Service does not see a logged-in
> user's `Z:` mapping. Always use `\\server\share\...` (`CLAUDE.md` §2, "Important Windows Rule").

---

## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| File dropped but no row in `/api/dumps` | Filename ends in `.part/.tmp/.partial/.copying`, or the client directory is not readable | Rename to `.dmp`; check `/actuator/health` → `dumpDirectories` |
| Row stuck in `STABILIZING` | File still being written, or `mtime` younger than `stable-file-check-delay`, or fewer than `stable-scans-required` scans so far | Wait a few scan cycles; confirm the copy has finished |
| Row stuck in `PENDING_IMPORT` | `processing.enabled: false`; no free worker; `next_eligible_at` in the future (backoff) | Check config; `/api/status` worker counts; `lastError` on the row |
| `dumpDirectories` health `OUT_OF_SERVICE` | A UNC share is unreachable right now | Fix the share; the scanner retries automatically — no restart needed |
| Import → `FAILED` with `importer not implemented` | `importer.mode` is `impdp`/`imp` (real execution is a `TODO`) | Set `mode: mock`, or wait for the real importer; then `POST /retry` |
| Import → `FAILED` with `The process cannot access the file…` | Antivirus/backup holding a lock | Usually transient — it will have retried; add an AV exclusion for the dump dirs |
| Startup aborts: "Oracle client executable(s) unusable" | Real mode + `fail-startup-on-missing-executable: true` + bad `impdp-path` | Fix the path, or set the flag to `false` |
| `Duplicate oracle-import client-id` at startup | Two client blocks share a `client-id` | Make client IDs unique |
| Port 8080 in use | Another process | `server.port: 0` (random) or set a free port |
| Everything works but nothing is scanned (default profile) | `clients: []` | Add client blocks to your config |

Turn up logging at runtime without a restart:

```bash
curl -s -X POST localhost:8080/actuator/loggers/com.demo.oracle_dump \
  -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
```

---

## Quick command reference

```bash
# build + test
./mvnw clean verify

# run (dev: in-memory DB, local dirs auto-created under /Users/chandragauro/temp, mock importer)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# run (windows: explicit switch, same Windows paths as the default profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=windows

# run the jar (default profile — Windows paths, file DB, clients: [] until configured)
java -jar target/oracle_dump-0.0.1-SNAPSHOT.jar

# status / inventory
curl -s localhost:8080/api/status | jq
curl -s 'localhost:8080/api/dumps?limit=50' | jq
curl -s 'localhost:8080/api/dumps?status=FAILED' | jq

# recover a FAILED / MISSING row
curl -s -X POST localhost:8080/api/dumps/<id>/retry | jq

# health
curl -s localhost:8080/actuator/health | jq

# feed a file (upload convention)
printf 'DATA%.0s' $(seq 1 1000) > <dir>/<name>.dmp.part && mv <dir>/<name>.dmp.part <dir>/<name>.dmp

# open the metadata DB: browser -> http://localhost:8080/h2-console
#   default profile URL: jdbc:h2:file:./data/oracle-import   (user sa, no password)
#   dev profile URL:     jdbc:h2:mem:oracle-import
# or the H2 shell against the file DB while the app runs:
java -cp ~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar org.h2.tools.Shell \
  -url "jdbc:h2:file:./data/oracle-import;AUTO_SERVER=TRUE" -user sa -password "" \
  -sql "SELECT STATUS, COUNT(*) FROM DUMP_FILE GROUP BY STATUS"
```

---

## References

- [`architecture.md`](architecture.md) — full architecture, diagrams, and design rationale.
- [`README.md`](README.md) — condensed feature overview.
- `CLAUDE.md` — the Windows Server runtime contract (sections cited inline as "`CLAUDE.md` §N").
- Spring Boot — running your application: <https://docs.spring.io/spring-boot/reference/using/running-your-application.html>
- Spring Boot Maven plugin (`spring-boot:run`, `repackage`): <https://docs.spring.io/spring-boot/maven-plugin/index.html>
- Spring Boot Actuator (`/actuator/health`, `/actuator/loggers`): <https://docs.spring.io/spring-boot/reference/actuator/index.html>
- Graceful shutdown: <https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html>
- Spring Boot + H2 console / embedded databases: <https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.h2-web-console>
- H2 database — features, URLs, `AUTO_SERVER`: <https://www.h2database.com/html/features.html>
- H2 tools (`Shell`, `Console`): <https://www.h2database.com/html/tutorial.html#command_line_tools>
- Spring Data JPA `@Query` / derived queries: <https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html>
- Oracle Data Pump `impdp`: <https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-data-pump-import-utility.html>
- Oracle `CREATE DIRECTORY`: <https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CREATE-DIRECTORY.html>
- WinSW: <https://github.com/winsw/winsw> · NSSM: <https://nssm.cc/>
- Windows file path formats (UNC): <https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats>
- `jq` (used in the examples): <https://jqlang.github.io/jq/>
