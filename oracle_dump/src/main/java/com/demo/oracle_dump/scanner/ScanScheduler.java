package com.demo.oracle_dump.scanner;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.lifecycle.PipelineState;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Drives {@link DirectoryScanner} on a fixed delay ({@code oracle-import.scanner.interval}). Uses
 * {@code fixedDelay} (not {@code fixedRate}) so a slow scan over a laggy UNC share never overlaps the
 * next one.
 */
@Slf4j
@Component
public class ScanScheduler {

	private final DirectoryScanner scanner;
	private final OracleImportProperties properties;
	private final PipelineState pipelineState;

	public ScanScheduler(DirectoryScanner scanner, OracleImportProperties properties,
			PipelineState pipelineState) {
		this.scanner = scanner;
		this.properties = properties;
		this.pipelineState = pipelineState;
	}

	@Scheduled(fixedDelayString = "${oracle-import.scanner.interval:30s}",
			initialDelayString = "${oracle-import.scanner.initial-delay:5s}")
	public void scan() {
		if (!properties.getScanner().isEnabled() || !pipelineState.isAcceptingWork()) {
			return;
		}
		try {
			ScanSummary summary = scanner.scanAll();
			if (summary.discovered() > 0 || summary.eligible() > 0 || summary.vanished() > 0) {
				log.info("Scan cycle: discovered={} eligible={} vanished={}", summary.discovered(),
						summary.eligible(), summary.vanished());
			}
		}
		catch (RuntimeException e) {
			log.error("Scan cycle failed", e);
		}
	}
}
