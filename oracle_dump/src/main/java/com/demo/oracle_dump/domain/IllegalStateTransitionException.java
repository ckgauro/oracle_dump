package com.demo.oracle_dump.domain;

/** Thrown when code attempts a lifecycle jump that {@link DumpStatus} does not permit. */
public class IllegalStateTransitionException extends RuntimeException {

	public IllegalStateTransitionException(Long recordId, DumpStatus from, DumpStatus to) {
		super("Illegal transition for dump record " + recordId + ": " + from + " -> " + to);
	}
}
