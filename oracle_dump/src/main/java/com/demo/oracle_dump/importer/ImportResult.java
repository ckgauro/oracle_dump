package com.demo.oracle_dump.importer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Outcome of one import attempt. A sealed type so callers must handle both arms — pattern-matched in
 * the processing service with a {@code switch}.
 */
public sealed interface ImportResult permits ImportResult.Success, ImportResult.Failure {

	Duration duration();

	Path logPath();

	/** Fold both arms into a single value. */
	default <T> T fold(Function<Success, T> onSuccess, Function<Failure, T> onFailure) {
		return switch (this) {
			case Success s -> onSuccess.apply(s);
			case Failure f -> onFailure.apply(f);
		};
	}

	default boolean succeeded() {
		return this instanceof Success;
	}

	// --- arms -----------------------------------------------------------

	record Success(Duration duration, Path logPath, String detail) implements ImportResult {
	}

	/**
	 * @param retryable whether the pipeline should schedule another attempt (transient Oracle/IO error)
	 *                  rather than parking the dump in FAILED
	 */
	record Failure(Duration duration, Path logPath, String message, boolean retryable,
			Integer exitCode, Throwable cause) implements ImportResult {

		public Optional<Integer> exitCodeOpt() {
			return Optional.ofNullable(exitCode);
		}
	}

	// --- factories ----------------------------------------------------

	static Success success(Duration duration, Path logPath, String detail) {
		return new Success(duration, logPath, detail);
	}

	static Failure retryableFailure(Duration duration, Path logPath, String message, Throwable cause) {
		return new Failure(duration, logPath, message, true, null, cause);
	}

	static Failure permanentFailure(Duration duration, Path logPath, String message, Integer exitCode,
			Throwable cause) {
		return new Failure(duration, logPath, message, false, exitCode, cause);
	}
}
