package com.demo.oracle_dump.scanner;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileStabilityCheckerTest {

	private OracleImportProperties props;
	private FileStabilityChecker checker;

	@BeforeEach
	void setUp() {
		props = new OracleImportProperties();
		props.getScanner().setStableScansRequired(2);
		props.getScanner().setStableFileCheckDelay(Duration.ofSeconds(30));
		checker = new FileStabilityChecker(props);
	}

	private DumpFileRecord record(long size, long mtime, int stableCount) {
		DumpFileRecord r = new DumpFileRecord("c", "f.dmp", "/x/f.dmp", size, mtime);
		r.setStableScanCount(stableCount);
		return r;
	}

	private ScannedFile scanned(long size, long mtime) {
		return new ScannedFile("c", Path.of("/x/f.dmp"), "f.dmp", size, mtime);
	}

	@Test
	void sizeChangeResetsCountAndIsNotEligible() {
		Instant now = Instant.now();
		var a = checker.assess(record(10, 1000, 1), scanned(17, 1000), now);
		assertThat(a.changed()).isTrue();
		assertThat(a.stableScanCount()).isEqualTo(1);
		assertThat(a.eligible()).isFalse();
	}

	@Test
	void needsRequiredNumberOfStableScans() {
		Instant now = Instant.now();
		long oldMtime = now.minusSeconds(120).toEpochMilli();
		// First stable observation -> count becomes 2 but still needs the delay; here delay satisfied.
		var a1 = checker.assess(record(20, oldMtime, 1), scanned(20, oldMtime), now);
		assertThat(a1.stableScanCount()).isEqualTo(2);
		assertThat(a1.eligible()).isTrue();
	}

	@Test
	void notEligibleWhileLastModifiedIsTooRecent() {
		Instant now = Instant.now();
		long freshMtime = now.minusSeconds(5).toEpochMilli();
		var a = checker.assess(record(20, freshMtime, 5), scanned(20, freshMtime), now);
		assertThat(a.changed()).isFalse();
		assertThat(a.eligible()).isFalse();
	}

	@Test
	void notEligibleWithOnlyOneStableScanEvenIfOld() {
		Instant now = Instant.now();
		long oldMtime = now.minusSeconds(300).toEpochMilli();
		var a = checker.assess(record(20, oldMtime, 0), scanned(20, oldMtime), now);
		assertThat(a.stableScanCount()).isEqualTo(1);
		assertThat(a.eligible()).isFalse();
	}
}
