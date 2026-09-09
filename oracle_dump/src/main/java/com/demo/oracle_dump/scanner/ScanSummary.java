package com.demo.oracle_dump.scanner;

/**
 * Aggregated counters for one or more client scans.
 *
 * @param discovered           dump files seen for the first time (new metadata rows)
 * @param eligible             files promoted to PENDING_IMPORT this cycle
 * @param vanished             tracked files that were no longer on disk
 * @param directoryUnavailable at least one client directory could not be read
 */
public record ScanSummary(int discovered, int eligible, int vanished, boolean directoryUnavailable) {

	public static ScanSummary empty() {
		return new ScanSummary(0, 0, 0, false);
	}

	public static ScanSummary unavailable() {
		return new ScanSummary(0, 0, 0, true);
	}

	public static ScanSummary of(int discovered, int eligible, int vanished) {
		return new ScanSummary(discovered, eligible, vanished, false);
	}

	public ScanSummary plus(ScanSummary other) {
		return new ScanSummary(
				discovered + other.discovered,
				eligible + other.eligible,
				vanished + other.vanished,
				directoryUnavailable || other.directoryUnavailable);
	}

	public ScanSummary withVanished(int v) {
		return new ScanSummary(discovered, eligible, v, directoryUnavailable);
	}
}
