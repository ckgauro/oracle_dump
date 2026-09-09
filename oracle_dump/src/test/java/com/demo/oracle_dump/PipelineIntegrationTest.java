package com.demo.oracle_dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.demo.oracle_dump.api.PipelineStatusService;
import com.demo.oracle_dump.domain.DumpFileRecord;
import com.demo.oracle_dump.domain.DumpFileRepository;
import com.demo.oracle_dump.domain.DumpStatus;
import com.demo.oracle_dump.processing.ImportDispatcher;
import com.demo.oracle_dump.scanner.DirectoryScanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end pipeline: drop a .dmp file, scan until it is stable, dispatch it, and verify the mock
 * importer drives it to IMPORTED. Also covers the copy-in-progress guard, the {@code *.part} ignore
 * rule, and content de-duplication.
 */
@SpringBootTest
class PipelineIntegrationTest {

	@TempDir
	static Path scanDir;
	@TempDir
	static Path logsDir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("oracle-import.scanner.enabled", () -> false);
		r.add("oracle-import.processing.enabled", () -> false);
		r.add("oracle-import.scanner.stable-file-check-delay", () -> "0s");
		r.add("oracle-import.scanner.stable-scans-required", () -> 2);
		r.add("oracle-import.importer.mode", () -> "mock");
		r.add("oracle-import.importer.mock-duration", () -> "10ms");
		r.add("oracle-import.importer.import-log-root", () -> logsDir.toString().replace('\\', '/'));
		r.add("oracle-import.clients[0].client-id", () -> "client-a");
		r.add("oracle-import.clients[0].enabled", () -> true);
		r.add("oracle-import.clients[0].dump-directory", () -> scanDir.toString().replace('\\', '/'));
		r.add("oracle-import.clients[0].source-schema", () -> "APP");
		r.add("oracle-import.clients[0].target-schema", () -> "CLIENT_A");
		r.add("oracle-import.clients[0].oracle-directory-name", () -> "CLIENT_A_IMPORT_DIR");
		r.add("oracle-import.clients[0].max-parallel-imports", () -> 1);
	}

	@Autowired
	DirectoryScanner scanner;
	@Autowired
	ImportDispatcher dispatcher;
	@Autowired
	DumpFileRepository repository;
	@Autowired
	PipelineStatusService statusService;

	@BeforeEach
	void clean() throws IOException {
		repository.deleteAll();
		try (var s = Files.newDirectoryStream(scanDir)) {
			for (Path p : s) {
				Files.deleteIfExists(p);
			}
		}
	}

	private Path writeDump(String name, String content) throws IOException {
		Path p = scanDir.resolve(name);
		Files.writeString(p, content);
		return p;
	}

	private DumpFileRecord only() {
		List<DumpFileRecord> all = repository.findAll();
		assertThat(all).hasSize(1);
		return all.get(0);
	}

	@Test
	void stableDumpIsScannedThenImported() throws Exception {
		writeDump("backup.dmp", "PAYLOAD-".repeat(64));

		scanner.scanAll();
		assertThat(only().getStatus()).isEqualTo(DumpStatus.STABILIZING);

		scanner.scanAll();
		DumpFileRecord eligible = only();
		assertThat(eligible.getStatus()).isEqualTo(DumpStatus.PENDING_IMPORT);

		int submitted = dispatcher.dispatch();
		assertThat(submitted).isEqualTo(1);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			DumpFileRecord done = repository.findById(eligible.getId()).orElseThrow();
			assertThat(done.getStatus()).isEqualTo(DumpStatus.IMPORTED);
			assertThat(done.getSha256()).hasSize(64);
			assertThat(done.getImportLogPath()).isNotBlank();
			assertThat(Files.exists(Path.of(done.getImportLogPath()))).isTrue();
			assertThat(done.getImportDurationMs()).isNotNull();
		});
	}

	@Test
	void fileStillGrowingIsNotEligible() throws Exception {
		Path p = writeDump("growing.dmp", "1".repeat(100));
		scanner.scanAll();
		assertThat(only().getStatus()).isEqualTo(DumpStatus.STABILIZING);

		Files.writeString(p, "2".repeat(500)); // size changed between scans -> copy in progress
		scanner.scanAll();
		assertThat(only().getStatus()).isEqualTo(DumpStatus.STABILIZING);
		assertThat(only().getStableScanCount()).isEqualTo(1);
	}

	@Test
	void partialUploadSuffixesAreIgnored() throws Exception {
		writeDump("backup.dmp.part", "still copying");
		writeDump("scratch.tmp", "junk");
		scanner.scanAll();
		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	void vanishedThenRestoredFileRecoversToImported() throws Exception {
		writeDump("recover.dmp", "RECOVER-".repeat(40));
		scanner.scanAll();
		scanner.scanAll();
		Long id = only().getId();
		assertThat(only().getStatus()).isEqualTo(DumpStatus.PENDING_IMPORT);

		// File disappears exactly while it is being claimed for checksum.
		Files.delete(scanDir.resolve("recover.dmp"));
		dispatcher.dispatch();
		await().atMost(Duration.ofSeconds(10)).until(
				() -> repository.findById(id).orElseThrow().getStatus() == DumpStatus.MISSING);

		// Operator puts a fresh copy back and hits POST /api/dumps/{id}/retry. The new mtime differs
		// from what the record stored, so the claimed record must be able to fall back to STABILIZING
		// (regression: this used to throw IllegalStateTransitionException QUEUED -> STABILIZING).
		writeDump("recover.dmp", "RECOVER-".repeat(40));
		statusService.retry(id);
		dispatcher.dispatch(); // claims -> QUEUED -> beginChecksum sees changed file -> STABILIZING

		await().atMost(Duration.ofSeconds(5)).until(
				() -> repository.findById(id).orElseThrow().getStatus() == DumpStatus.STABILIZING);
		assertThat(repository.findById(id).orElseThrow().getLastError()).isNull();

		scanner.scanAll();
		scanner.scanAll();
		dispatcher.dispatch();
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertThat(repository.findById(id).orElseThrow().getStatus())
						.isEqualTo(DumpStatus.IMPORTED));
	}

	@Test
	void identicalContentForSameClientIsMarkedDuplicate() throws Exception {
		String payload = "SAME-BYTES-".repeat(50);
		writeDump("first.dmp", payload);
		scanner.scanAll();
		scanner.scanAll();
		dispatcher.dispatch();

		Long firstId = repository.findAll().get(0).getId();
		await().atMost(Duration.ofSeconds(10)).until(
				() -> repository.findById(firstId).orElseThrow().getStatus() == DumpStatus.IMPORTED);

		writeDump("second.dmp", payload);
		scanner.scanAll();
		scanner.scanAll();
		dispatcher.dispatch();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			DumpFileRecord dup = repository.findAll().stream()
					.filter(r -> r.getFileName().equals("second.dmp"))
					.findFirst().orElseThrow();
			assertThat(dup.getStatus()).isEqualTo(DumpStatus.DUPLICATE);
			assertThat(dup.getDuplicateOfId()).isEqualTo(firstId);
		});
	}
}
