package com.demo.oracle_dump.importer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.demo.oracle_dump.config.ClientDefinition;
import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;

import lombok.extern.slf4j.Slf4j;

/**
 * Real Windows {@code impdp.exe} importer. <strong>Execution is intentionally not implemented yet</strong>
 * (CLAUDE.md §10) — the deployment architecture (Data Pump DIRECTORY, staging, credentials) is not
 * confirmed. What <em>is</em> implemented and unit-tested is the safe command construction:
 * separate {@link ProcessBuilder} arguments (no {@code cmd.exe}, no concatenated command string,
 * CLAUDE.md §11–§12) and child-process environment setup (CLAUDE.md §14).
 *
 * <p>This bean is only created when {@code oracle-import.importer.mode} is {@code impdp} or {@code imp};
 * see {@link ImporterConfig}.
 */
@Slf4j
public class OracleDataPumpImporter implements DumpImporter {

	private final OracleImportProperties properties;
	private final Mode mode;

	public OracleDataPumpImporter(OracleImportProperties properties, Mode mode) {
		this.properties = properties;
		this.mode = mode;
	}

	@Override
	public Mode mode() {
		return mode;
	}

	@Override
	public ImportResult importDump(ImportRequest request) {
		// TODO: Implement Oracle import for Windows Server.
		//
		//   1. Stage the dump into an Oracle-accessible directory if required (DumpStagingService),
		//      since impdp reads via a DIRECTORY object, not an arbitrary Windows/UNC path (§15, §16).
		//   2. Resolve credentials for client.getDatabaseConnectionName() from a secret store.
		//   3. ProcessBuilder pb = buildProcessBuilder(client, stagedFileName, connectionArgument);
		//      applyOracleEnvironment(pb.environment());
		//      pb.redirectErrorStream(true);
		//      pb.redirectOutput(request.importLogPath().toFile());
		//   4. Process p = pb.start();
		//      boolean done = p.waitFor(properties.getImporter().getImportTimeout().toMillis(), MILLISECONDS);
		//      if (!done) { p.destroyForcibly(); return permanentFailure(..., "timeout", null, null); }
		//   5. int exit = p.exitValue();
		//      exit == 0            -> ImportResult.success(...)
		//      exit in {retryable}  -> ImportResult.retryableFailure(...)
		//      else                 -> ImportResult.permanentFailure(..., exit, null)
		//   6. Never log the Oracle password. Capture stdout+stderr to the log file only.
		throw new UnsupportedOperationException(
				"Oracle import (mode=" + mode + ") implementation pending — see CLAUDE.md §10");
	}

	// ------------------------------------------------------------------
	// Implemented + testable: command / environment construction.
	// ------------------------------------------------------------------

	/**
	 * Build the {@link ProcessBuilder} for a Data Pump import using discrete arguments.
	 *
	 * @param client             tenant config (schemas, Oracle DIRECTORY object)
	 * @param dumpFileName       bare dump file name as seen through the Oracle DIRECTORY (not a full path)
	 * @param connectionArgument e.g. {@code "system@//db-host:1521/ORCLPDB1"} — credentials are added by
	 *                           the caller from a secret store and must never be logged
	 */
	public ProcessBuilder buildProcessBuilder(ClientDefinition client, String dumpFileName,
			String connectionArgument) {
		String impdp = requireExecutable();
		List<String> args = new ArrayList<>();
		args.add(impdp);
		args.add(connectionArgument);
		args.add("DIRECTORY=" + Objects.requireNonNull(client.getOracleDirectoryName(),
				"oracle-directory-name is required for a real impdp import"));
		args.add("DUMPFILE=" + dumpFileName);
		args.add("LOGFILE=" + dumpFileName + ".impdp.log");
		if (client.getSourceSchema() != null && !client.getSourceSchema().isBlank()) {
			args.add("SCHEMAS=" + client.getSourceSchema());
			args.add("REMAP_SCHEMA=" + client.getSourceSchema() + ":" + client.getTargetSchema());
		}
		ProcessBuilder pb = new ProcessBuilder(args);
		applyOracleEnvironment(pb.environment());
		return pb;
	}

	/** Set ORACLE_HOME / TNS_ADMIN on the child only, never on the JVM/server (CLAUDE.md §14). */
	public void applyOracleEnvironment(Map<String, String> childEnv) {
		OracleImportProperties.OracleClient cfg = properties.getOracleClient();
		if (cfg.getOracleHome() != null && !cfg.getOracleHome().isBlank()) {
			childEnv.put("ORACLE_HOME", cfg.getOracleHome());
		}
		if (cfg.getTnsAdmin() != null && !cfg.getTnsAdmin().isBlank()) {
			childEnv.put("TNS_ADMIN", cfg.getTnsAdmin());
		}
	}

	private String requireExecutable() {
		String path = mode == Mode.IMPDP
				? properties.getOracleClient().getImpdpPath()
				: properties.getOracleClient().getImpPath();
		if (path == null || path.isBlank()) {
			throw new IllegalStateException("No Oracle client executable configured for mode " + mode);
		}
		Path exe = Path.of(path);
		if (log.isDebugEnabled()) {
			log.debug("Resolved Oracle client executable for {}: {}", mode, exe);
		}
		return exe.toString();
	}
}
