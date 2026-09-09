package com.demo.oracle_dump.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DumpStatusTest {

	@Test
	void happyPathTransitionsAreAllowed() {
		assertThat(DumpStatus.DISCOVERED.canTransitionTo(DumpStatus.STABILIZING)).isTrue();
		assertThat(DumpStatus.STABILIZING.canTransitionTo(DumpStatus.PENDING_IMPORT)).isTrue();
		assertThat(DumpStatus.PENDING_IMPORT.canTransitionTo(DumpStatus.QUEUED)).isTrue();
		assertThat(DumpStatus.QUEUED.canTransitionTo(DumpStatus.CHECKSUMMING)).isTrue();
		assertThat(DumpStatus.CHECKSUMMING.canTransitionTo(DumpStatus.IMPORTING)).isTrue();
		assertThat(DumpStatus.IMPORTING.canTransitionTo(DumpStatus.IMPORTED)).isTrue();
	}

	@Test
	void illegalJumpsAreRejected() {
		assertThat(DumpStatus.DISCOVERED.canTransitionTo(DumpStatus.IMPORTING)).isFalse();
		assertThat(DumpStatus.IMPORTED.canTransitionTo(DumpStatus.IMPORTING)).isFalse();
		assertThat(DumpStatus.DUPLICATE.canTransitionTo(DumpStatus.PENDING_IMPORT)).isFalse();
	}

	@Test
	void retryLoopsBackToPending() {
		assertThat(DumpStatus.IMPORTING.canTransitionTo(DumpStatus.PENDING_IMPORT)).isTrue();
		assertThat(DumpStatus.CHECKSUMMING.canTransitionTo(DumpStatus.PENDING_IMPORT)).isTrue();
		assertThat(DumpStatus.FAILED.canTransitionTo(DumpStatus.PENDING_IMPORT)).isTrue();
	}

	@Test
	void claimedRecordCanFallBackToStabilizingWhenFileChangesUnderUs() {
		// Regression: a restored/changed file detected in beginChecksum must be able to re-stabilize.
		assertThat(DumpStatus.QUEUED.canTransitionTo(DumpStatus.STABILIZING)).isTrue();
		assertThat(DumpStatus.CHECKSUMMING.canTransitionTo(DumpStatus.STABILIZING)).isTrue();
	}

	@Test
	void recordEnforcesTransitions() {
		DumpFileRecord r = new DumpFileRecord("c", "a.dmp", "/tmp/a.dmp", 1, 1);
		r.transitionTo(DumpStatus.STABILIZING);
		r.transitionTo(DumpStatus.PENDING_IMPORT);
		assertThatThrownBy(() -> r.transitionTo(DumpStatus.IMPORTED))
				.isInstanceOf(IllegalStateTransitionException.class);
	}

	@Test
	void inFlightAndTerminalHelpers() {
		assertThat(DumpStatus.IMPORTING.isInFlight()).isTrue();
		assertThat(DumpStatus.QUEUED.isInFlight()).isTrue();
		assertThat(DumpStatus.IMPORTED.isTerminal()).isTrue();
		assertThat(DumpStatus.DUPLICATE.isTerminal()).isTrue();
		assertThat(DumpStatus.FAILED.isTerminal()).isFalse();
	}
}
