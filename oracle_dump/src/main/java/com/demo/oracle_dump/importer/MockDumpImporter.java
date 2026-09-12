package com.demo.oracle_dump.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Default importer: no Oracle contact. Verifies the dump is readable, writes a realistic-looking log
 * file, sleeps for a configured duration to mimic a real import, and reports success. Safe for dev
 * and CI, and the reference implementation for the log-file contract real importers must honour.
 */
@Slf4j
@Component
public class MockDumpImporter implements DumpImporter {

	private final OracleImportProperties properties;

	public MockDumpImporter(OracleImportProperties properties) {
		this.properties = properties;
	}

	@Override
	public Mode mode() {
		return Mode.MOCK;
	}

	@Override
	public ImportResult importDump(ImportRequest request) {
		Instant start = Instant.now();
		Path dump = request.dumpPath();
		Path logPath = request.importLogPath();

		try {
			if (!Files.isReadable(dump)) {
				String msg = "Dump not readable at import time: " + dump;
				writeLog(logPath, List.of(header(request), "ERROR " + msg));
				return ImportResult.retryableFailure(elapsed(start), logPath, msg, null);
			}

			long size = Files.size(dump);
			sleepQuietly(properties.getImporter().getMockDuration());

			writeLog(logPath, List.of(
					header(request),
					"Connected to: MOCK Oracle Database",
					"Master table \"%s\".\"SYS_IMPORT_SCHEMA_01\" successfully loaded".formatted(
							request.client().getTargetSchema()),
					"Starting \"%s\".\"SYS_IMPORT_SCHEMA_01\":  DIRECTORY=%s DUMPFILE=%s REMAP_SCHEMA=%s:%s"
							.formatted(request.client().getTargetSchema(),
									orDash(request.client().getOracleDirectoryName()),
									dump.getFileName(),
									orDash(request.client().getSourceSchema()),
									request.client().getTargetSchema()),
					"Processing object type SCHEMA_EXPORT/TABLE/TABLE_DATA",
					"Bytes processed: " + size,
					"Job \"%s\".\"SYS_IMPORT_SCHEMA_01\" successfully completed".formatted(
							request.client().getTargetSchema()),
					"MOCK import finished in " + properties.getImporter().getMockDuration()));

			Duration took = elapsed(start);
			log.info("[mock] imported {} ({} bytes) for client {} in {}", dump.getFileName(), size,
					request.client().getClientId(), took);
			return ImportResult.success(took, logPath, "mock import of " + size + " bytes");
		}
		catch (UncheckedIOException | IOException e) {
			return ImportResult.retryableFailure(elapsed(start), logPath,
					"Mock importer IO error: " + e.getMessage(), e);
		}
	}

	private static String header(ImportRequest r) {
		return "== MOCK impdp == client=%s schema=%s sha256=%s dump=%s started=%s".formatted(
				r.client().getClientId(), r.client().getTargetSchema(), r.sha256(), r.dumpPath(),
				r.startedAt());
	}

	private static String orDash(String s) {
		return (s == null || s.isBlank()) ? "-" : s;
	}

	private static Duration elapsed(Instant start) {
		return Duration.between(start, Instant.now());
	}

	private static void writeLog(Path logPath, List<String> lines) throws IOException {
		Files.createDirectories(logPath.getParent());
		Files.write(logPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static void sleepQuietly(Duration d) {
		try {
			Thread.sleep(Math.max(0, d.toMillis()));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
