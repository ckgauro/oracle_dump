package com.demo.oracle_dump.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import com.demo.oracle_dump.config.ClientDefinition;
import com.demo.oracle_dump.config.OracleImportProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MockDumpImporterTest {

	private ClientDefinition client() {
		ClientDefinition c = new ClientDefinition();
		c.setClientId("client-a");
		c.setSourceSchema("APP");
		c.setTargetSchema("CLIENT_A");
		c.setOracleDirectoryName("CLIENT_A_IMPORT_DIR");
		return c;
	}

	@Test
	void writesLogAndReportsSuccess(@TempDir Path dir) throws Exception {
		Path dump = dir.resolve("backup.dmp");
		Files.writeString(dump, "x".repeat(2048));
		Path logFile = dir.resolve("logs/client-a/2026/09/import.log");

		OracleImportProperties props = new OracleImportProperties();
		props.getImporter().setMockDuration(Duration.ofMillis(5));
		MockDumpImporter importer = new MockDumpImporter(props);

		ImportResult result = importer.importDump(
				new ImportRequest(client(), dump, "deadbeef", logFile, Instant.now()));

		assertThat(result.succeeded()).isTrue();
		assertThat(Files.exists(logFile)).isTrue();
		String log = Files.readString(logFile);
		assertThat(log).contains("REMAP_SCHEMA=APP:CLIENT_A");
		assertThat(log).contains("successfully completed");
		assertThat(log).doesNotContainIgnoringCase("password");
	}

	@Test
	void missingDumpIsRetryableFailure(@TempDir Path dir) {
		Path missing = dir.resolve("nope.dmp");
		Path logFile = dir.resolve("logs/import.log");
		ImportResult result = new MockDumpImporter(new OracleImportProperties()).importDump(
				new ImportRequest(client(), missing, "abc", logFile, Instant.now()));

		assertThat(result).isInstanceOf(ImportResult.Failure.class);
		assertThat(((ImportResult.Failure) result).retryable()).isTrue();
	}
}
