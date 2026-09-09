package com.demo.oracle_dump.lifecycle;

import java.time.Duration;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.processing.ProcessingConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Graceful start/stop for the Windows Service lifecycle (CLAUDE.md §20).
 *
 * <ul>
 *   <li><b>start</b>: allow the scanner and dispatcher to accept work.</li>
 *   <li><b>stop</b>: stop accepting new work immediately, then wait up to
 *       {@code shutdownGracePeriod} for in-flight imports to finish before the executor is torn down.</li>
 * </ul>
 * Anything still running when the grace period elapses is reset to {@code PENDING_IMPORT} by the
 * stale-record reaper on the next startup, so no dump is ever lost — only delayed.
 */
@Component
public class ProcessingLifecycle implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(ProcessingLifecycle.class);

	private final PipelineState pipelineState;
	private final ThreadPoolTaskExecutor importExecutor;
	private final OracleImportProperties properties;

	private volatile boolean running;

	public ProcessingLifecycle(PipelineState pipelineState,
			@Qualifier(ProcessingConfig.IMPORT_EXECUTOR) ThreadPoolTaskExecutor importExecutor,
			OracleImportProperties properties) {
		this.pipelineState = pipelineState;
		this.importExecutor = importExecutor;
		this.properties = properties;
	}

	@Override
	public void start() {
		pipelineState.startAcceptingWork();
		running = true;
		log.info("Oracle dump pipeline started (maxWorkers={}, importer mode={})",
				properties.getProcessing().getMaxWorkers(), properties.getImporter().getMode());
	}

	@Override
	public void stop() {
		log.info("Oracle dump pipeline stopping; no new work will be accepted");
		pipelineState.stopAcceptingWork();
		drainInFlight(properties.getProcessing().getShutdownGracePeriod());
		running = false;
	}

	private void drainInFlight(Duration grace) {
		long deadline = System.nanoTime() + grace.toNanos();
		int active;
		while ((active = importExecutor.getThreadPoolExecutor().getActiveCount()) > 0
				&& System.nanoTime() < deadline) {
			log.info("Waiting for {} in-flight import(s) to finish...", active);
			sleep(1_000);
		}
		int remaining = importExecutor.getThreadPoolExecutor().getActiveCount();
		if (remaining > 0) {
			log.warn("Grace period elapsed with {} import(s) still running; they will be reclaimed on "
					+ "next startup", remaining);
		}
		else {
			log.info("All in-flight imports completed cleanly");
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getPhase() {
		// Stop earlier than the web server and the executor bean so we drain before they close.
		return Integer.MAX_VALUE - 100;
	}
}
