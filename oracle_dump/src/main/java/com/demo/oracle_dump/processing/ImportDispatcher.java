package com.demo.oracle_dump.processing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRecord;
import com.demo.oracle_dump.domain.DumpFileRepository;
import com.demo.oracle_dump.lifecycle.PipelineState;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Polls the metadata DB for eligible dumps, claims only as many as there is free worker capacity for,
 * and hands each to a {@link DumpProcessor} on the bounded import executor.
 *
 * <p>Claiming uses a status-guarded atomic UPDATE ({@link DumpFileRepository#claim}), so running more
 * than one service instance against a shared database is safe. H2 is fine for a single instance; use
 * a real RDBMS for multi-instance (CLAUDE.md §19).
 */
@Slf4j
@Component
public class ImportDispatcher {

	private final DumpFileRepository repository;
	private final DumpProcessor processor;
	private final ProcessingSteps steps;
	private final StaleRecordReaper reaper;
	private final ThreadPoolTaskExecutor executor;
	private final OracleImportProperties properties;
	private final PipelineState pipelineState;
	private final TransactionTemplate txTemplate;
	private final String instanceId = "svc-" + UUID.randomUUID().toString().substring(0, 8);
	private final AtomicBoolean startupReclaimDone = new AtomicBoolean(false);

	public ImportDispatcher(DumpFileRepository repository, DumpProcessor processor, ProcessingSteps steps,
			StaleRecordReaper reaper,
			@Qualifier(ProcessingConfig.IMPORT_EXECUTOR) ThreadPoolTaskExecutor executor,
			OracleImportProperties properties, PipelineState pipelineState,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.processor = processor;
		this.steps = steps;
		this.reaper = reaper;
		this.executor = executor;
		this.properties = properties;
		this.pipelineState = pipelineState;
		this.txTemplate = new TransactionTemplate(transactionManager);
	}

	@Scheduled(fixedDelayString = "${oracle-import.processing.poll-interval:10s}",
			initialDelayString = "${oracle-import.processing.poll-interval:10s}")
	public void tick() {
		if (!properties.getProcessing().isEnabled() || !pipelineState.isAcceptingWork()) {
			return;
		}
		try {
			if (startupReclaimDone.compareAndSet(false, true)) {
				reaper.reclaimAll();
			}
			reaper.reclaimStale();
			dispatch();
		}
		catch (RuntimeException e) {
			log.error("Dispatcher tick failed", e);
		}
	}

	/** Claim and submit up to the free capacity. Returns the number of records submitted. */
	public int dispatch() {
		int free = freeCapacity();
		if (free <= 0) {
			return 0;
		}
		int batch = Math.min(free, properties.getProcessing().getDispatchBatchSize());
		List<Long> candidateIds = txTemplate.execute(s ->
				repository.findClaimable(Instant.now(), Limit.of(batch))
						.stream().map(DumpFileRecord::getId).toList());
		if (candidateIds == null || candidateIds.isEmpty()) {
			return 0;
		}

		int submitted = 0;
		for (Long id : candidateIds) {
			boolean won = Boolean.TRUE.equals(txTemplate.execute(s ->
					repository.claim(id, instanceId, Instant.now()) == 1));
			if (!won) {
				continue; // lost the race to another worker/instance
			}
			try {
				executor.execute(() -> processor.process(id));
				submitted++;
			}
			catch (RejectedExecutionException reject) {
				steps.requeue(id, Instant.now().plusSeconds(2), "executor saturated");
				break;
			}
		}
		if (submitted > 0) {
			log.info("Dispatched {} dump(s) for import ({} free slot(s))", submitted, free);
		}
		return submitted;
	}

	private int freeCapacity() {
		ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
		int inSystem = pool.getActiveCount() + pool.getQueue().size();
		int ceiling = pool.getMaximumPoolSize() + properties.getProcessing().getMaxWorkers();
		return Math.max(0, ceiling - inSystem);
	}

	public String instanceId() {
		return instanceId;
	}
}
