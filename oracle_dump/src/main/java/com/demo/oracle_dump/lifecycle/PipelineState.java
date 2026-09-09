package com.demo.oracle_dump.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Shared run-state flag. When the Windows Service is asked to stop, {@link ProcessingLifecycle} flips
 * this to {@code false}; the scanner and dispatcher check it and stop taking on new work while
 * in-flight imports are allowed to finish (CLAUDE.md §20).
 */
@Component
public class PipelineState {

	private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

	public boolean isAcceptingWork() {
		return acceptingWork.get();
	}

	public void startAcceptingWork() {
		acceptingWork.set(true);
	}

	public void stopAcceptingWork() {
		acceptingWork.set(false);
	}
}
