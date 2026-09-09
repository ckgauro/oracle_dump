package com.demo.oracle_dump.processing;

import java.time.Duration;
import java.time.Instant;

import com.demo.oracle_dump.config.OracleImportProperties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

	private RetryPolicy policy(int maxAttempts, Duration base, Duration max) {
		OracleImportProperties p = new OracleImportProperties();
		p.getProcessing().setMaxAttempts(maxAttempts);
		p.getProcessing().setRetryBackoff(base);
		p.getProcessing().setRetryBackoffMax(max);
		return new RetryPolicy(p);
	}

	@Test
	void exponentialBackoffDoublesPerAttempt() {
		RetryPolicy p = policy(10, Duration.ofSeconds(1), Duration.ofHours(1));
		assertThat(p.delayFor(1)).isEqualTo(Duration.ofSeconds(1));
		assertThat(p.delayFor(2)).isEqualTo(Duration.ofSeconds(2));
		assertThat(p.delayFor(3)).isEqualTo(Duration.ofSeconds(4));
		assertThat(p.delayFor(4)).isEqualTo(Duration.ofSeconds(8));
	}

	@Test
	void backoffIsCappedAtMax() {
		RetryPolicy p = policy(50, Duration.ofSeconds(1), Duration.ofSeconds(10));
		assertThat(p.delayFor(20)).isEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void canRetryUntilMaxAttempts() {
		RetryPolicy p = policy(3, Duration.ofSeconds(1), Duration.ofSeconds(10));
		assertThat(p.canRetry(1)).isTrue();
		assertThat(p.canRetry(2)).isTrue();
		assertThat(p.canRetry(3)).isFalse();
	}

	@Test
	void nextAttemptAtAddsDelay() {
		RetryPolicy p = policy(5, Duration.ofMinutes(1), Duration.ofHours(1));
		Instant from = Instant.parse("2026-09-08T10:00:00Z");
		assertThat(p.nextAttemptAt(1, from)).isEqualTo(Instant.parse("2026-09-08T10:01:00Z"));
	}
}
