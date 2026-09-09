package com.demo.oracle_dump.processing;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.io.WindowsPaths;

import org.springframework.stereotype.Component;

/**
 * Builds per-client, date-partitioned import-log paths, e.g.
 * {@code D:\OracleImporter\logs\imports\client-a\2026\09\9d28f4d6-...log} (CLAUDE.md §22).
 * Only the resulting path is stored in metadata; the (potentially huge) impdp output goes to the file.
 */
@Component
public class ImportLogPaths {

	private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

	private final Path root;

	public ImportLogPaths(OracleImportProperties properties) {
		this.root = WindowsPaths.toNormalizedPath(properties.getImporter().getImportLogRoot());
	}

	public Path newLogFile(String clientId, UUID id) {
		ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
		return root.resolve(clientId)
				.resolve(now.format(YEAR))
				.resolve(now.format(MONTH))
				.resolve(id + ".log");
	}

	public Path root() {
		return root;
	}
}
