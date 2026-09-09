package com.demo.oracle_dump.scanner;

import java.time.Duration;
import java.time.Instant;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRecord;

import org.springframework.stereotype.Component;

/**
 * Pure decision logic for "is this file done being copied?" (CLAUDE.md §6).
 *
 * <p>A file is eligible only when, across {@code stableScansRequired} consecutive scans, its size and
 * last-modified timestamp have not changed, AND its last-modified timestamp is at least
 * {@code stableFileCheckDelay} in the past. Kept side-effect free so it is trivially unit-testable.
 */
@Component
public class FileStabilityChecker {

	private final OracleImportProperties properties;

	public FileStabilityChecker(OracleImportProperties properties) {
		this.properties = properties;
	}

	public Assessment assess(DumpFileRecord existing, ScannedFile scanned, Instant now) {
		OracleImportProperties.Scanner cfg = properties.getScanner();
		boolean changed = !existing.matchesOnDisk(scanned.sizeBytes(), scanned.lastModifiedEpochMs());

		if (changed) {
			return new Assessment(1, true, false);
		}

		int newCount = existing.getStableScanCount() + 1;
		Duration quietFor = Duration.between(Instant.ofEpochMilli(scanned.lastModifiedEpochMs()), now);
		boolean longEnough = !quietFor.isNegative()
				&& quietFor.compareTo(cfg.getStableFileCheckDelay()) >= 0;
		boolean eligible = newCount >= cfg.getStableScansRequired() && longEnough;
		return new Assessment(newCount, false, eligible);
	}

	/**
	 * @param stableScanCount value to persist for the next cycle
	 * @param changed         true if size/mtime moved since last scan (copy still in progress)
	 * @param eligible        true if the file may now be handed to the import pipeline
	 */
	public record Assessment(int stableScanCount, boolean changed, boolean eligible) {
	}
}
