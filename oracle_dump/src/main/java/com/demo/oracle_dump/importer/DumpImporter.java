package com.demo.oracle_dump.importer;

import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;

/**
 * Strategy for turning a validated dump file into an Oracle import. Implementations are selected at
 * runtime by {@link com.demo.oracle_dump.config.OracleImportProperties.Importer#getMode()}.
 *
 * <p>Functional interface: a trivial importer can be supplied as a lambda in tests, e.g.
 * {@code req -> ImportResult.success(Duration.ZERO, req.importLogPath(), "noop")}.
 */
@FunctionalInterface
public interface DumpImporter {

	ImportResult importDump(ImportRequest request);

	/** Which mode this implementation serves. Overridden by real beans; defaulted for lambdas. */
	default Mode mode() {
		return Mode.MOCK;
	}
}
