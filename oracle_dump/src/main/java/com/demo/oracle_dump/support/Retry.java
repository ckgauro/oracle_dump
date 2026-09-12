package com.demo.oracle_dump.support;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Tiny functional retry helper for in-call transient failures (e.g. a single flaky UNC stat).
 *
 * <p>This is <em>not</em> the pipeline-level retry — durable retries with backoff are persisted on the
 * {@code DumpFileRecord}. Use this only for short, bounded, in-memory operations.
 *
 * <pre>{@code
 * BasicFileAttributes attrs = Retry.of("stat " + path)
 *         .maxAttempts(3)
 *         .delay(Duration.ofMillis(250))
 *         .retryIf(IoFailure.classify(?)::retryable)   // predicate on the thrown exception
 *         .call(() -> Files.readAttributes(path, BasicFileAttributes.class));
 * }</pre>
 */
@Slf4j
public final class Retry {

	private final String description;
	private int maxAttempts = 3;
	private Duration delay = Duration.ofMillis(200);
	private double multiplier = 2.0;
	private Predicate<Throwable> retryIf = t -> true;

	private Retry(String description) {
		this.description = description;
	}

	public static Retry of(String description) {
		return new Retry(description);
	}

	public Retry maxAttempts(int maxAttempts) {
		this.maxAttempts = Math.max(1, maxAttempts);
		return this;
	}

	public Retry delay(Duration delay) {
		this.delay = delay;
		return this;
	}

	public Retry multiplier(double multiplier) {
		this.multiplier = multiplier;
		return this;
	}

	public Retry retryIf(Predicate<Throwable> retryIf) {
		this.retryIf = retryIf;
		return this;
	}

	/** Execute {@code body}, retrying on matching exceptions. The last failure is rethrown. */
	public <T> T call(Supplier<T> body) {
		RuntimeException last = null;
		long currentDelayMs = delay.toMillis();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return body.get();
			}
			catch (RuntimeException ex) {
				last = ex;
				if (attempt == maxAttempts || !retryIf.test(unwrap(ex))) {
					throw ex;
				}
				log.debug("Retry {}/{} for [{}] after {}: {}", attempt, maxAttempts, description,
						ex.getClass().getSimpleName(), ex.getMessage());
				sleep(currentDelayMs);
				currentDelayMs = (long) (currentDelayMs * multiplier);
			}
		}
		throw last; // unreachable
	}

	public void run(Runnable body) {
		call(() -> {
			body.run();
			return null;
		});
	}

	private static Throwable unwrap(RuntimeException ex) {
		return ex.getCause() != null ? ex.getCause() : ex;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted during retry backoff", ie);
		}
	}
}
