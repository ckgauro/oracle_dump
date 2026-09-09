package com.demo.oracle_dump.processing;

import java.time.Duration;
import java.time.Instant;

import com.demo.oracle_dump.config.OracleImportProperties;

import org.springframework.stereotype.Component;

/**
 * Exponential backoff with a ceiling. Attempt {@code n} (1-based) waits
 * {@code min(retryBackoff * 2^(n-1), retryBackoffMax)}.
 */
@Component
public class RetryPolicy {

	private final OracleImportProperties properties;

	public RetryPolicy(OracleImportProperties properties) {
		this.properties = properties;
	}

	public boolean canRetry(int attemptCount) {
		return attemptCount < properties.getProcessing().getMaxAttempts();
	}

	public Instant nextAttemptAt(int attemptCount, Instant from) {
		return from.plus(delayFor(attemptCount));
	}

	public Duration delayFor(int attemptCount) {
		OracleImportProperties.Processing cfg = properties.getProcessing();
		int shift = Math.min(30, Math.max(0, attemptCount - 1));
		Duration delay = cfg.getRetryBackoff().multipliedBy(1L << shift);
		Duration max = cfg.getRetryBackoffMax();
		return (delay.isNegative() || delay.compareTo(max) > 0) ? max : delay;
	}
}
