package com.demo.oracle_dump.processing;

import java.time.Instant;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets dump records stuck in an in-flight state ({@code QUEUED/CHECKSUMMING/IMPORTING}) past the
 * stale timeout — the signature of a worker thread or whole service instance that died mid-import.
 * Such records go back to {@code PENDING_IMPORT} and are retried.
 */
@Component
public class StaleRecordReaper {

	private static final Logger log = LoggerFactory.getLogger(StaleRecordReaper.class);

	private final DumpFileRepository repository;
	private final OracleImportProperties properties;

	public StaleRecordReaper(DumpFileRepository repository, OracleImportProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	/** Reclaim records whose claim is older than the configured stale timeout. */
	@Transactional
	public int reclaimStale() {
		Instant threshold = Instant.now().minus(properties.getProcessing().getStaleProcessingTimeout());
		int n = repository.reclaimStale(Instant.now(), threshold);
		if (n > 0) {
			log.warn("Reclaimed {} stale in-flight dump record(s) (claim older than {})", n,
					properties.getProcessing().getStaleProcessingTimeout());
		}
		return n;
	}

	/** Reclaim <em>all</em> in-flight records regardless of age. Used once at startup. */
	@Transactional
	public int reclaimAll() {
		int n = repository.reclaimStale(Instant.now(), Instant.now());
		if (n > 0) {
			log.warn("Startup reclaim: reset {} record(s) that were in-flight at last shutdown", n);
		}
		return n;
	}
}
