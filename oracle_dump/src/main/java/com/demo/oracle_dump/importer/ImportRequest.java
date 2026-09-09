package com.demo.oracle_dump.importer;

import java.nio.file.Path;
import java.time.Instant;

import com.demo.oracle_dump.config.ClientDefinition;

/**
 * Everything an importer needs for one dump, assembled by the processing service.
 *
 * @param client        tenant configuration (schemas, Oracle DIRECTORY name, connection name)
 * @param dumpPath      source dump on the Spring Boot host (may be local or UNC)
 * @param sha256        verified content hash, for logging / provenance
 * @param importLogPath file the importer must write its stdout/stderr transcript to
 * @param startedAt     wall-clock start, used to compute duration
 */
public record ImportRequest(
		ClientDefinition client,
		Path dumpPath,
		String sha256,
		Path importLogPath,
		Instant startedAt) {
}
