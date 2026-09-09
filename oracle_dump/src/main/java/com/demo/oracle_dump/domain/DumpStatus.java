package com.demo.oracle_dump.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a single dump file in the metadata store.
 *
 * <pre>
 *   DISCOVERED ──▶ STABILIZING ──▶ PENDING_IMPORT ──▶ QUEUED ──▶ CHECKSUMMING ──▶ IMPORTING ──▶ IMPORTED
 *        ▲              │                 ▲   │            │            │              │
 *        └──────────────┘                 │   └────────────┴────────────┴──────────────┘  (retry / reclaim)
 *                                         │                                   │
 *                                         └───────────────────────────────────┴──▶ FAILED / DUPLICATE / MISSING
 * </pre>
 *
 * Allowed transitions are declared once, here, and enforced by {@link DumpFileRecord#transitionTo}.
 */
public enum DumpStatus {

	DISCOVERED,
	STABILIZING,
	PENDING_IMPORT,
	QUEUED,
	CHECKSUMMING,
	IMPORTING,
	IMPORTED,
	DUPLICATE,
	FAILED,
	MISSING;

	private static final Map<DumpStatus, Set<DumpStatus>> ALLOWED = new EnumMap<>(DumpStatus.class);

	static {
		ALLOWED.put(DISCOVERED, EnumSet.of(STABILIZING, PENDING_IMPORT, MISSING));
		ALLOWED.put(STABILIZING, EnumSet.of(STABILIZING, PENDING_IMPORT, DISCOVERED, MISSING));
		ALLOWED.put(PENDING_IMPORT, EnumSet.of(QUEUED, STABILIZING, MISSING));
		// A claimed record whose file changed or vanished under us drops back to STABILIZING/MISSING
		// (detected in ProcessingSteps.beginChecksum before the checksum starts).
		ALLOWED.put(QUEUED, EnumSet.of(CHECKSUMMING, STABILIZING, PENDING_IMPORT, MISSING, FAILED));
		ALLOWED.put(CHECKSUMMING,
				EnumSet.of(IMPORTING, DUPLICATE, STABILIZING, PENDING_IMPORT, MISSING, FAILED));
		ALLOWED.put(IMPORTING, EnumSet.of(IMPORTED, PENDING_IMPORT, FAILED, MISSING));
		// Terminal-ish states. A changed file on disk re-opens the record (handled by the scanner),
		// and an operator may requeue a FAILED record explicitly.
		ALLOWED.put(IMPORTED, EnumSet.of(DISCOVERED));
		ALLOWED.put(DUPLICATE, EnumSet.of(DISCOVERED));
		ALLOWED.put(FAILED, EnumSet.of(PENDING_IMPORT, DISCOVERED));
		ALLOWED.put(MISSING, EnumSet.of(DISCOVERED, STABILIZING, PENDING_IMPORT));
	}

	public boolean canTransitionTo(DumpStatus target) {
		return this == target || ALLOWED.getOrDefault(this, Set.of()).contains(target);
	}

	public boolean isTerminal() {
		return this == IMPORTED || this == DUPLICATE;
	}

	public boolean isInFlight() {
		return this == QUEUED || this == CHECKSUMMING || this == IMPORTING;
	}
}
