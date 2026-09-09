package com.demo.oracle_dump.io;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Classifies filesystem / network-share failures on Windows into a {@link Kind} so the pipeline can
 * decide whether to retry (CLAUDE.md §24, §25).
 *
 * <p>Classification is expressed as an ordered list of {@link Rule}s (predicate + kind). The first
 * matching rule wins; anything unmatched is {@link Kind#UNKNOWN} and treated as retryable-once by the
 * caller's retry policy.
 */
public record IoFailure(Kind kind, String message, Throwable cause) {

	public enum Kind {
		/** File is mid-copy or opened by AV/backup. Retry later. */
		SHARING_VIOLATION(true),
		/** {@code \\server\share} not reachable right now. Retry later. */
		NETWORK_UNAVAILABLE(true),
		/** ACL / service-account problem, possibly transient AV lock. Retry a few times. */
		ACCESS_DENIED(true),
		/** File genuinely not there anymore. Do not retry blindly. */
		NOT_FOUND(false),
		/** Anything else. */
		UNKNOWN(true);

		private final boolean retryable;

		Kind(boolean retryable) {
			this.retryable = retryable;
		}

		public boolean retryable() {
			return retryable;
		}
	}

	/** A single classification rule: if {@code matches} holds, the failure is {@code kind}. */
	private record Rule(Predicate<Throwable> matches, Kind kind) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule(t -> t instanceof NoSuchFileException, Kind.NOT_FOUND),
			new Rule(t -> messageContains(t, "the system cannot find the file", "the system cannot find the path"),
					Kind.NOT_FOUND),
			new Rule(t -> t instanceof AccessDeniedException, Kind.ACCESS_DENIED),
			new Rule(t -> messageContains(t, "access is denied"), Kind.ACCESS_DENIED),
			new Rule(t -> messageContains(t,
					"being used by another process",
					"the process cannot access the file",
					"sharing violation",
					"lock violation"), Kind.SHARING_VIOLATION),
			new Rule(t -> messageContains(t,
					"the network path was not found",
					"the network name cannot be found",
					"the specified network name is no longer available",
					"the network connection was lost",
					"a socket operation was attempted to an unreachable network"), Kind.NETWORK_UNAVAILABLE));

	public static IoFailure classify(Throwable t) {
		Kind kind = RULES.stream()
				.filter(rule -> rule.matches().test(t))
				.map(Rule::kind)
				.findFirst()
				.orElse(Kind.UNKNOWN);
		return new IoFailure(kind, describe(t), t);
	}

	public boolean retryable() {
		return kind.retryable();
	}

	private static boolean messageContains(Throwable t, String... needles) {
		String haystack = fullMessage(t).toLowerCase(Locale.ROOT);
		for (String n : needles) {
			if (haystack.contains(n)) {
				return true;
			}
		}
		return false;
	}

	private static String fullMessage(Throwable t) {
		StringBuilder sb = new StringBuilder();
		for (Throwable c = t; c != null && sb.length() < 4000; c = c.getCause()) {
			if (c.getMessage() != null) {
				sb.append(c.getMessage()).append(" | ");
			}
		}
		return sb.toString();
	}

	private static String describe(Throwable t) {
		String m = Optional.ofNullable(t.getMessage()).orElse(t.getClass().getSimpleName());
		return t.getClass().getSimpleName() + ": " + m;
	}

	/** Wrap a checked {@link IOException} thrown from a lambda body. */
	public static RuntimeException wrap(IOException e) {
		return new UncheckedPipelineIoException(e);
	}

	/** Marker so callers can unwrap and re-classify. */
	public static final class UncheckedPipelineIoException extends RuntimeException {
		public UncheckedPipelineIoException(IOException cause) {
			super(cause);
		}
	}
}
