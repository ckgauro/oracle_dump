# Windows Server Specific Requirements

The target runtime environment for this application is **Windows Server**.

The application must therefore be designed and tested with Windows filesystem paths, UNC network shares, Oracle client utilities on Windows, and Windows service execution in mind.

---

# 1. Supported File Locations

Oracle dump files may exist in:

```text
Local Windows drives

C:\OracleDumps\ClientA
D:\Backup\ClientB
```

or on network shares:

```text
\\fileserver01\oracle-dumps\ClientA
\\nas01\backups\ClientB
```

The application must support both local paths and UNC paths.

Do not assume a Linux-style filesystem.

Example configuration:

```yaml
oracle-import:
  clients:

    - client-id: client-a
      enabled: true
      dump-directory: "D:/OracleDumps/ClientA"
      schema: CLIENT_A
      max-parallel-imports: 1

    - client-id: client-b
      enabled: true
      dump-directory: "//nas01/oracle-dumps/ClientB"
      schema: CLIENT_B
      max-parallel-imports: 1
```

Prefer forward slashes inside YAML where possible because they reduce escaping problems.

For example:

```yaml
dump-directory: "D:/OracleDumps/ClientA"
```

instead of:

```yaml
dump-directory: "D:\\OracleDumps\\ClientA"
```

Both may work, but forward slashes are simpler for configuration.

---

# 2. Windows Network Shares

The application may process dumps located on UNC network shares.

Examples:

```text
\\nas01\oracle\ClientA
\\fileserver02\backup\ClientB
```

Do not assume that a mapped drive such as:

```text
Z:
```

will exist when the application runs as a Windows Service.

Mapped network drives are normally associated with a logged-in user session and may not be visible to Windows Services.

Therefore, for production, prefer UNC paths:

```text
\\server\share\folder
```

instead of:

```text
Z:\folder
```

---

# 3. Windows Service Account

The Spring Boot application will likely run as a Windows Service.

The Windows Service account must have permission to:

```text
Read dump directories

Read network shares

Execute Oracle utilities

Write application logs

Write import logs

Access H2 or production metadata database

Access temporary/staging directories
```

If using a network share, do not run the service under:

```text
LocalSystem
```

unless that account has explicitly been granted remote access.

Prefer a dedicated Windows/domain service account.

Example:

```text
DOMAIN\oracle-import-service
```

Grant only the permissions required by the application.

---

# 4. Windows Path Handling

Always use Java:

```java
Path
Paths
Files
```

for filesystem handling.

Do not manually concatenate paths.

Bad:

```java
String path =
    baseDirectory + "\\" + fileName;
```

Preferred:

```java
Path path =
    baseDirectory.resolve(fileName);
```

Normalize paths:

```java
Path normalized =
    path.toAbsolutePath().normalize();
```

The application must correctly support:

```text
C:\OracleDumps\ClientA\file.dmp

D:\Backup\file.dmp

\\nas01\oracle\ClientA\file.dmp
```

---

# 5. Windows Case Sensitivity

Windows filesystems such as NTFS are normally case-insensitive.

Therefore:

```text
Backup.dmp

backup.dmp
```

may refer to the same physical file.

Do not depend on filename case for uniqueness.

Checksum and client identity remain the primary duplicate detection mechanism.

---

# 6. File Stability on Windows

Windows copy operations may expose a destination file before copying has completed.

For example:

```text
20 GB dump copy starts

scanner sees:

backup.dmp = 3 GB

copy continues

backup.dmp = 10 GB

copy continues

backup.dmp = 20 GB
```

The application must not start checksum or Oracle import while a file is still being written.

Use file stability detection based on:

```text
file size

last modified timestamp
```

and optionally attempt to open the file before processing.

However, do not rely solely on Java file locking because Windows/network-share locking behavior may vary.

Preferred stability strategy:

```text
scan 1:
size = 10 GB

scan 2:
size = 17 GB
=> skip

scan 3:
size = 20 GB

scan 4:
size = 20 GB
last modified unchanged
=> eligible
```

---

# 7. Recommended Upload Convention

If the upstream system can be controlled, strongly prefer:

```text
backup.dmp.part
```

while copying.

Once copy completes, rename it to:

```text
backup.dmp
```

The scanner must ignore:

```text
*.part
*.tmp
*.partial
*.copying
```

This is much safer than trying to detect an actively copied `.dmp` file.

---

# 8. Windows Long Path Support

Some dump paths may become long.

Example:

```text
\\nas01\oracle-backups\client-a\production\2026\09\08\...
```

Avoid assumptions about the old Windows `MAX_PATH` limit.

Use Java NIO APIs rather than old `java.io.File` APIs where possible.

Prefer:

```java
Path
Files
DirectoryStream
```

over legacy filesystem code.

---

# 9. Oracle Client on Windows

The future Oracle import functionality will run using Oracle client utilities installed on Windows.

Possible executables:

```text
imp.exe

impdp.exe

sqlplus.exe
```

Configuration should allow the executable location to be specified.

Example:

```yaml
oracle-import:

  oracle-client:
    impdp-path: "C:/Oracle/product/19c/client_1/bin/impdp.exe"
    imp-path: "C:/Oracle/product/19c/client_1/bin/imp.exe"
```

Do not assume Oracle binaries are always available through:

```text
PATH
```

Explicit configuration is preferable in production.

---

# 10. Oracle Import TODO for Windows

The actual Oracle import remains a TODO.

Example structure:

```java
@Override
public ImportResult importDump(
        ClientDefinition client,
        Path dumpPath) {

    // TODO:
    // Implement Oracle import for Windows Server.
    //
    // Possible Oracle utilities:
    //
    // C:\Oracle\product\19c\client_1\bin\impdp.exe
    //
    // or:
    //
    // C:\Oracle\product\19c\client_1\bin\imp.exe
    //
    // Use ProcessBuilder.
    //
    // DO NOT build:
    //
    // cmd.exe /c "impdp username/password ..."
    //
    // unless shell execution is absolutely necessary.
    //
    // Prefer executing impdp.exe directly.
    //
    // Capture:
    // stdout
    // stderr
    // exit code
    // duration
    //
    // Never log Oracle passwords.

    throw new UnsupportedOperationException(
        "Oracle import implementation pending"
    );
}
```

---

# 11. ProcessBuilder on Windows

Prefer:

```java
ProcessBuilder
```

with separate arguments.

Example future pattern:

```java
ProcessBuilder processBuilder =
    new ProcessBuilder(
        impdpExecutable,
        connectionArgument,
        "DIRECTORY=" + oracleDirectory,
        "DUMPFILE=" + dumpFile,
        "REMAP_SCHEMA=" + sourceSchema + ":" + targetSchema
    );
```

Do not create one large command string such as:

```java
String command =
    "impdp " +
    username +
    "/" +
    password +
    " DIRECTORY=...";
```

Separate arguments reduce quoting and command injection problems.

---

# 12. Avoid cmd.exe Where Possible

Do not unnecessarily execute:

```text
cmd.exe /c
```

For example, avoid:

```java
new ProcessBuilder(
    "cmd.exe",
    "/c",
    "impdp ..."
);
```

Prefer direct execution:

```java
new ProcessBuilder(
    "C:/Oracle/.../impdp.exe",
    ...
);
```

Use `cmd.exe` only when Windows shell behavior is genuinely required.

---

# 13. Windows Executable Validation

At application startup, optionally validate configured Oracle executables.

Example:

```text
Does impdp.exe exist?

Is it readable?

Can the service account execute it?
```

Expose failures through:

```text
startup validation

health endpoint

application logs
```

Do not wait until the first 20 GB import to discover that `impdp.exe` does not exist.

---

# 14. Oracle Environment Variables

Oracle utilities may depend on environment variables such as:

```text
PATH

ORACLE_HOME

TNS_ADMIN
```

Allow configuration where necessary.

Example:

```yaml
oracle-import:

  oracle-client:
    oracle-home: "C:/Oracle/product/19c/client_1"
    tns-admin: "C:/Oracle/network/admin"
```

When creating the process:

```java
Map<String, String> environment =
    processBuilder.environment();

environment.put(
    "ORACLE_HOME",
    oracleHome
);

environment.put(
    "TNS_ADMIN",
    tnsAdmin
);
```

Do not globally alter the server environment from application code.

Configure only the child Oracle process where practical.

---

# 15. Oracle Data Pump Directory

Important:

`impdp.exe` generally does not directly import any arbitrary Windows file path.

For example, this local Spring Boot path:

```text
D:\OracleDumps\ClientA\backup.dmp
```

does not automatically mean Oracle can use:

```text
DUMPFILE=D:\OracleDumps\ClientA\backup.dmp
```

Data Pump normally uses an Oracle DIRECTORY object.

Example Oracle configuration:

```sql
CREATE DIRECTORY IMPORT_DUMPS AS
'D:\OracleDataPump';
```

Then:

```text
DIRECTORY=IMPORT_DUMPS
DUMPFILE=backup.dmp
```

The path must be accessible to the Oracle Database service account on the database server.

Therefore the application should distinguish between:

```text
source dump location
```

and:

```text
Oracle Data Pump location
```

---

# 16. Dump Staging on Windows

The future system may require a staging process:

```text
Network Share

\\nas01\backup\ClientA\backup.dmp

        |
        v

Windows Oracle Server

D:\OracleDataPump\ClientA\backup.dmp

        |
        v

Oracle DIRECTORY

CLIENT_A_IMPORT_DIR

        |
        v

impdp.exe
```

Leave room for:

```java
DumpStagingService
```

Possible responsibilities:

```text
Copy dump to Oracle-accessible directory

Verify staged file size

Verify checksum if required

Avoid recopying existing staged files

Clean staged files according to retention policy
```

Do not implement this until the actual Oracle deployment architecture is confirmed.

---

# 17. Windows Network Copy Performance

For 10 MB to 20+ GB files, avoid unnecessary network copies.

If the dump is already on a share accessible by the Oracle Database server, use that architecture instead of automatically copying files again.

If copying is required, it should be:

```text
streamed

buffered

restart-aware if practical

observable
```

Do not load a dump into memory before copying.

---

# 18. Checksum on Windows / Network Share

Full SHA-256 calculation means the entire file must be read.

For:

```text
20 GB file
```

on:

```text
\\nas01\oracle\backup.dmp
```

this means 20 GB of network I/O.

Therefore use the multi-stage approach from the main specification:

```text
1. Client
2. Path/name
3. File size
4. Last modified timestamp
5. Existing metadata
6. Full SHA-256 only where required
```

Once checksum has been calculated and:

```text
size

lastModified

path
```

remain unchanged, reuse the stored checksum.

---

# 19. H2 on Windows

For development, H2 may use:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/oracle-import
```

For Windows Server production, avoid placing the H2 database file on a network share.

Bad:

```text
\\nas01\application-data\metadata.mv.db
```

Prefer local disk if H2 is used:

```text
D:\OracleImporter\data\
```

However, if the application may eventually run as multiple application instances, use a proper shared database such as:

```text
PostgreSQL

Oracle

SQL Server
```

instead of H2.

H2 should primarily be treated as a development or single-instance option.

---

# 20. Windows Service Deployment

The application should be deployable as a Windows Service.

Possible approaches include:

```text
WinSW

NSSM

Java Service Wrapper
```

The application itself must not depend on a particular wrapper.

Expected service lifecycle:

```text
Windows starts service

Spring Boot starts

Scanner starts

Workers start

...

Windows sends service stop

Scanner stops

No new dumps are accepted

Active work shuts down gracefully

Spring Boot exits
```

---

# 21. Example Windows Directory Layout

Recommended conceptual layout:

```text
D:\OracleImporter
│
├── app
│   └── oracle-dump-importer.jar
│
├── config
│   └── application.yml
│
├── data
│   └── metadata
│
├── logs
│   ├── application.log
│   │
│   └── imports
│       ├── client-a
│       └── client-b
│
└── temp
```

Dump files may live elsewhere:

```text
D:\OracleDumps

or

\\nas01\oracle-dumps
```

---

# 22. Import Log Paths

Example:

```text
D:\OracleImporter\logs\imports\client-a\2026\09\
```

Example file:

```text
9d28f4d6-6efa-430a-a61b.log
```

Store the path in metadata.

Do not store massive `impdp` output directly in H2.

---

# 23. Windows File Deletion

Do not automatically delete dump files after import.

On Windows, deletion may fail because:

```text
another process has the file open

antivirus is scanning it

backup software is scanning it

network share is temporarily unavailable
```

Deletion/archive should be a separate configurable operation.

Initial application behavior:

```text
leave original dump unchanged
```

and record:

```text
IMPORTED
```

in metadata.

---

# 24. Antivirus Considerations

Large `.dmp` files may be scanned by antivirus or endpoint security products.

This can temporarily affect:

```text
file access

checksum performance

file locking

import performance
```

The application must treat temporary file access failures as potentially retryable.

Do not assume every `AccessDeniedException` means permanent failure.

Log enough information to distinguish repeated permanent failures from temporary access problems.

---

# 25. Windows File Sharing Errors

Handle Windows-related IO errors such as:

```text
Access is denied

The process cannot access the file because it is being used by another process

The network path was not found

The specified network name is no longer available

The network connection was lost
```

Translate these into useful application errors.

Where appropriate, mark them retryable.

---

# 26. Client Example for Windows

Example complete client configuration:

```yaml
oracle-import:

  scanner:
    enabled: true
    interval: 30s
    stable-file-check-delay: 30s

  processing:
    max-workers: 4
    stale-processing-timeout: 2h

  checksum:
    algorithm: SHA-256
    buffer-size-mb: 8

  oracle-client:
    impdp-path: "C:/Oracle/product/19c/client_1/bin/impdp.exe"
    imp-path: "C:/Oracle/product/19c/client_1/bin/imp.exe"
    oracle-home: "C:/Oracle/product/19c/client_1"
    tns-admin: "C:/Oracle/network/admin"

  importer:
    mode: mock

  clients:

    - client-id: client-a
      enabled: true

      dump-directory: "//nas01/oracle-dumps/client-a"

      target-schema: CLIENT_A

      oracle-directory-name: CLIENT_A_IMPORT_DIR

      max-parallel-imports: 1

    - client-id: client-b
      enabled: true

      dump-directory: "D:/OracleDumps/ClientB"

      target-schema: CLIENT_B

      oracle-directory-name: CLIENT_B_IMPORT_DIR

      max-parallel-imports: 1
```

---

# 27. Client Configuration Model

Each client should eventually support:

```text
clientId

enabled

dumpDirectory

sourceSchema

targetSchema

oracleDirectoryName

databaseConnectionName

maxParallelImports
```

Example:

```yaml
- client-id: customer-01
  dump-directory: "//backup-server/oracle/customer-01"
  source-schema: APP
  target-schema: CUSTOMER01_APP
  oracle-directory-name: CUSTOMER01_IMPORT
  database-connection-name: production-oracle
  max-parallel-imports: 1
```

This is better than assuming directory name and schema name are always identical.

---

# 28. Network Availability Health Check

Add a health check for configured dump locations.

For each client:

```text
directory exists

directory readable
```

Example status:

```text
client-a dump directory: UP

client-b dump directory: DOWN
Reason: network path unavailable
```

Do not continuously perform expensive directory scans through Actuator health requests.

The health check should remain lightweight.

---

# 29. Startup Behavior

A temporary unavailable network share should not necessarily stop the complete Spring Boot application from starting.

For example:

```text
\\nas01\oracle-dumps
```

could be temporarily unavailable during server boot.

Preferred behavior:

```text
application starts

metadata DB starts

scanner runs

share unavailable

error logged

client temporarily skipped

scanner retries later
```

Make this behavior configurable if necessary.

---

# 30. Production Recommendation

For Windows Server production deployment:

```text
Spring Boot Windows Service
        |
        |
        +---- Metadata Database
        |
        +---- UNC Network Share
        |
        +---- Oracle Client
                  |
                  +---- imp.exe
                  |
                  +---- impdp.exe
```

Use a dedicated Windows service account with explicit access to:

```text
UNC network share

Oracle client binaries

application directories

Oracle connection
```

---

# Important Windows Rule

Do not use mapped drives such as:

```text
Z:\OracleDumps
```

as the primary production configuration for a Windows Service.

Use:

```text
\\server\share\OracleDumps
```

because Windows Services may not inherit user-specific mapped drives.

---

# Important Oracle Rule

Do not assume:

```text
Spring Boot can see a dump
```

means:

```text
Oracle Data Pump can see the dump.
```

These are two different filesystem contexts.

Keep these concepts separate in the architecture:

```text
Source Dump Directory
        |
        v
Spring Boot

Oracle DIRECTORY
        |
        v
Oracle Database Server
```

A future `DumpStagingService` may bridge the two.

---

# Instructions to Claude for Windows

When generating this project:

1. Assume the production operating system is Windows Server.
2. Use Java NIO `Path` and `Files`.
3. Support local Windows paths and UNC network paths.
4. Do not depend on mapped network drives.
5. Do not assume Linux shell commands.
6. Do not generate Bash scripts as part of the core application.
7. Oracle CLI executables will be `.exe`.
8. Prefer direct `ProcessBuilder` execution of `imp.exe` or `impdp.exe`.
9. Do not depend on `cmd.exe` unless necessary.
10. Treat Windows/network-share file access errors carefully.
11. Keep large-file operations streaming.
12. Ensure paths containing spaces are handled correctly.
13. Keep Oracle Data Pump DIRECTORY separate from the Spring source directory.
14. Make Oracle executable paths configurable.
15. Keep all real Oracle execution as TODO for the initial implementation.
