package com.demo.oracle_dump.processing;

import java.nio.file.Files;
import java.nio.file.Path;

import com.demo.oracle_dump.config.OracleImportProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumServiceTest {

	@Test
	void hashesFileWithKnownSha256(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("backup.dmp");
		Files.writeString(f, "hello world");

		OracleImportProperties props = new OracleImportProperties();
		props.getChecksum().setBufferSizeMb(1);
		ChecksumService service = new ChecksumService(props);

		ChecksumService.Result result = service.hash(f);

		// echo -n "hello world" | sha256sum
		assertThat(result.sha256Hex())
				.isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
		assertThat(result.bytesRead()).isEqualTo(11);
	}

	@Test
	void largeFileAcrossMultipleBufferReads(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("big.dmp");
		byte[] chunk = new byte[64 * 1024];
		try (var out = Files.newOutputStream(f)) {
			for (int i = 0; i < 40; i++) {
				out.write(chunk);
			}
		}
		OracleImportProperties props = new OracleImportProperties();
		props.getChecksum().setBufferSizeMb(1);

		ChecksumService.Result result = new ChecksumService(props).hash(f);
		assertThat(result.bytesRead()).isEqualTo(40L * 64 * 1024);
		assertThat(result.sha256Hex()).hasSize(64);
	}
}
