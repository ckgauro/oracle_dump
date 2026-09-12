package com.demo.oracle_dump.processing;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.demo.oracle_dump.config.ClientRegistry;
import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.importer.DumpImporter;
import com.demo.oracle_dump.importer.ImportRequest;
import com.demo.oracle_dump.importer.ImportResult;
import com.demo.oracle_dump.io.IoFailure;
import com.demo.oracle_dump.processing.ProcessingSteps.ChecksumWork;
import com.demo.oracle_dump.processing.ProcessingSteps.ImportDecision;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs the full pipeline for one already-claimed dump record on a worker thread:
 * <pre>
 *   beginChecksum ──▶ [hash file]  ──▶ completeChecksum ──▶ [acquire client permit] ──▶ [impdp] ──▶ record result
 * </pre>
 * Slow phases (hashing, import) run outside any DB transaction. The per-client {@link Semaphore}
 * enforces {@code maxParallelImports} and is always released in a {@code finally}.
 */
@Slf4j
@Component
public class DumpProcessor {

	private final ProcessingSteps steps;
	private final ChecksumService checksumService;
	private final DumpImporter importer;
	private final ClientRegistry clients;
	private final OracleImportProperties properties;

	public DumpProcessor(ProcessingSteps steps, ChecksumService checksumService, DumpImporter importer,
			ClientRegistry clients, OracleImportProperties properties) {
		this.steps = steps;
		this.checksumService = checksumService;
		this.importer = importer;
		this.clients = clients;
		this.properties = properties;
	}

	public void process(long recordId) {
		try {
			steps.beginChecksum(recordId).ifPresentOrElse(
					this::runFrom,
					() -> log.debug("Record {} not eligible to checksum; released", recordId));
		}
		catch (IoFailure.UncheckedPipelineIoException e) {
			IoFailure failure = IoFailure.classify(e.getCause());
			steps.recordFailure(recordId, ImportResult.retryableFailure(Duration.ZERO, null,
					"IO error during processing: " + failure.message(), e.getCause()));
		}
		catch (RuntimeException e) {
			log.error("Unexpected error processing record {}", recordId, e);
			steps.abort(recordId, "unexpected: " + e);
		}
	}

	private void runFrom(ChecksumWork work) {
		long recordId = work.recordId();
		String sha256 = work.canReuse()
				? work.reusableSha256()
				: checksumService.hash(work.dumpPath()).sha256Hex();
		if (work.canReuse()) {
			log.debug("Reused stored checksum for record {}", recordId);
		}

		ImportDecision decision = steps.completeChecksum(recordId, sha256);
		switch (decision) {
			case ImportDecision.Duplicate d ->
					log.info("Record {} short-circuited as duplicate of {}", recordId, d.originalId());
			case ImportDecision.Aborted a -> steps.abort(recordId, a.reason());
			case ImportDecision.Proceed p -> runImport(recordId, p.request());
		}
	}

	private void runImport(long recordId, ImportRequest request) {
		Semaphore permits = clients.require(request.client().getClientId()).importPermits();
		Duration waitBudget = properties.getProcessing().getPollInterval();
		boolean acquired = false;
		try {
			acquired = permits.tryAcquire(Math.max(1, waitBudget.toSeconds()), TimeUnit.SECONDS);
			if (!acquired) {
				steps.requeue(recordId, Instant.now().plusSeconds(5),
						"no import permit free for client " + request.client().getClientId());
				return;
			}

			ImportResult result = invokeImporter(request);
			result.fold(
					success -> {
						steps.recordSuccess(recordId, success);
						return null;
					},
					failure -> {
						steps.recordFailure(recordId, failure);
						return null;
					});
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			steps.requeue(recordId, Instant.now().plusSeconds(30), "interrupted while awaiting permit");
		}
		finally {
			if (acquired) {
				permits.release();
			}
		}
	}

	private ImportResult invokeImporter(ImportRequest request) {
		try {
			return importer.importDump(request);
		}
		catch (UnsupportedOperationException e) {
			// Real Oracle import is still a TODO (mode=impdp/imp). Treat as retryable so nothing is lost.
			return ImportResult.retryableFailure(Duration.ZERO, request.importLogPath(),
					"importer not implemented: " + e.getMessage(), e);
		}
		catch (IoFailure.UncheckedPipelineIoException e) {
			IoFailure f = IoFailure.classify(e.getCause());
			return new ImportResult.Failure(Duration.ZERO, request.importLogPath(),
					"IO error during import: " + f.message(), f.retryable(), null, e.getCause());
		}
		catch (RuntimeException e) {
			return ImportResult.retryableFailure(Duration.ZERO, request.importLogPath(),
					"importer threw " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
		}
	}
}
