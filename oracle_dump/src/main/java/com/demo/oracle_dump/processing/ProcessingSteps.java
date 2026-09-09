package com.demo.oracle_dump.processing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.demo.oracle_dump.config.ClientRegistry;
import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRecord;
import com.demo.oracle_dump.domain.DumpFileRepository;
import com.demo.oracle_dump.domain.DumpStatus;
import com.demo.oracle_dump.importer.ImportRequest;
import com.demo.oracle_dump.importer.ImportResult;
import com.demo.oracle_dump.io.IoFailure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short, transactional database steps of processing one dump. The slow work (hashing 20 GB,
 * running impdp) happens <em>between</em> these calls in {@link DumpProcessor}, so no DB transaction
 * is ever held open across it.
 */
@Component
public class ProcessingSteps {

	private static final Logger log = LoggerFactory.getLogger(ProcessingSteps.class);
	private static final int MAX_ERROR_LEN = 8_000;

	private final DumpFileRepository repository;
	private final ClientRegistry clients;
	private final RetryPolicy retryPolicy;
	private final ImportLogPaths logPaths;
	private final OracleImportProperties properties;

	public ProcessingSteps(DumpFileRepository repository, ClientRegistry clients, RetryPolicy retryPolicy,
			ImportLogPaths logPaths, OracleImportProperties properties) {
		this.repository = repository;
		this.clients = clients;
		this.retryPolicy = retryPolicy;
		this.logPaths = logPaths;
		this.properties = properties;
	}

	/**
	 * Verify the claimed record's file is still present and byte-for-byte stable, then move it to
	 * {@link DumpStatus#CHECKSUMMING}. Returns empty (and re-parks the record) if the file vanished or
	 * changed under us.
	 */
	@Transactional
	public Optional<ChecksumWork> beginChecksum(long recordId) {
		DumpFileRecord record = load(recordId);
		if (record.getStatus() != DumpStatus.QUEUED) {
			log.warn("Record {} not in QUEUED ({}); skipping", recordId, record.getStatus());
			return Optional.empty();
		}
		Path path = Path.of(record.getAbsolutePath());
		BasicFileAttributes attrs = statOrNull(path);
		if (attrs == null) {
			parkMissing(record, "file not found at checksum time: " + path);
			return Optional.empty();
		}
		if (!record.matchesOnDisk(attrs.size(), attrs.lastModifiedTime().toMillis())) {
			record.setSizeBytes(attrs.size());
			record.setLastModifiedEpochMs(attrs.lastModifiedTime().toMillis());
			record.setSha256(null);
			record.setStableScanCount(1);
			record.setWorkerId(null);
			record.setClaimedAt(null);
			record.transitionTo(DumpStatus.STABILIZING);
			log.info("Record {} changed on disk during claim; back to STABILIZING", recordId);
			return Optional.empty();
		}

		record.transitionTo(DumpStatus.CHECKSUMMING);
		boolean reusable = record.hasReusableChecksum(attrs.size(), attrs.lastModifiedTime().toMillis());
		return Optional.of(new ChecksumWork(recordId, record.getClientId(), path,
				reusable ? record.getSha256() : null));
	}

	/**
	 * Store the hash, check for a duplicate (same client + content already imported/importing), and if
	 * unique move to {@link DumpStatus#IMPORTING} and build the {@link ImportRequest}.
	 */
	@Transactional
	public ImportDecision completeChecksum(long recordId, String sha256) {
		DumpFileRecord record = load(recordId);
		if (record.getStatus() != DumpStatus.CHECKSUMMING) {
			return new ImportDecision.Aborted("unexpected status " + record.getStatus());
		}
		record.setSha256(sha256);

		Optional<DumpFileRecord> sibling = repository
				.findChecksumSiblings(record.getClientId(), sha256, recordId).stream().findFirst();
		if (sibling.isPresent()) {
			record.setDuplicateOfId(sibling.get().getId());
			record.transitionTo(DumpStatus.DUPLICATE);
			record.setWorkerId(null);
			record.setClaimedAt(null);
			log.info("Record {} is a duplicate of {} (sha256={})", recordId, sibling.get().getId(), sha256);
			return new ImportDecision.Duplicate(sibling.get().getId());
		}

		Instant now = Instant.now();
		Path logFile = logPaths.newLogFile(record.getClientId(), UUID.randomUUID());
		record.transitionTo(DumpStatus.IMPORTING);
		record.setImportStartedAt(now);
		record.setImportLogPath(logFile.toString());
		record.incrementAttemptCount();

		var client = clients.require(record.getClientId()).definition();
		ImportRequest request = new ImportRequest(client, Path.of(record.getAbsolutePath()), sha256,
				logFile, now);
		return new ImportDecision.Proceed(request);
	}

	@Transactional
	public void recordSuccess(long recordId, ImportResult.Success result) {
		DumpFileRecord record = load(recordId);
		Instant now = Instant.now();
		record.transitionTo(DumpStatus.IMPORTED);
		record.setImportFinishedAt(now);
		record.setImportDurationMs(result.duration().toMillis());
		if (result.logPath() != null) {
			record.setImportLogPath(result.logPath().toString());
		}
		record.setWorkerId(null);
		record.setClaimedAt(null);
		record.setLastError(null);
		log.info("Imported record {} client='{}' file='{}' in {}", recordId, record.getClientId(),
				record.getFileName(), result.duration());
	}

	@Transactional
	public void recordFailure(long recordId, ImportResult.Failure result) {
		DumpFileRecord record = load(recordId);
		Instant now = Instant.now();
		record.setLastError(truncate(result.message()));
		record.setLastErrorAt(now);
		record.setImportDurationMs(result.duration().toMillis());
		record.setWorkerId(null);
		record.setClaimedAt(null);

		boolean retry = result.retryable() && retryPolicy.canRetry(record.getAttemptCount());
		if (retry) {
			Instant next = retryPolicy.nextAttemptAt(record.getAttemptCount(), now);
			record.setNextEligibleAt(next);
			record.transitionTo(DumpStatus.PENDING_IMPORT);
			log.warn("Import attempt {} failed for record {} ({}); retry at {}",
					record.getAttemptCount(), recordId, result.message(), next);
		}
		else {
			record.transitionTo(DumpStatus.FAILED);
			log.error("Import permanently failed for record {} after {} attempt(s): {}", recordId,
					record.getAttemptCount(), result.message());
		}
	}

	/** Put the record back in the queue without consuming an attempt (e.g. no import permit free). */
	@Transactional
	public void requeue(long recordId, Instant when, String reason) {
		DumpFileRecord record = load(recordId);
		record.setWorkerId(null);
		record.setClaimedAt(null);
		record.setNextEligibleAt(when);
		if (record.getStatus() != DumpStatus.PENDING_IMPORT) {
			record.transitionTo(DumpStatus.PENDING_IMPORT);
		}
		log.info("Requeued record {} at {} : {}", recordId, when, reason);
	}

	@Transactional
	public void abort(long recordId, String reason) {
		DumpFileRecord record = load(recordId);
		record.setWorkerId(null);
		record.setClaimedAt(null);
		record.setLastError(truncate(reason));
		record.setLastErrorAt(Instant.now());
		if (record.getStatus().isInFlight()) {
			record.transitionTo(DumpStatus.PENDING_IMPORT);
			record.setNextEligibleAt(Instant.now().plus(Duration.ofMinutes(1)));
		}
		log.warn("Aborted processing of record {} : {}", recordId, reason);
	}

	private DumpFileRecord load(long recordId) {
		return repository.findByIdForUpdate(recordId)
				.orElseThrow(() -> new IllegalStateException("Dump record vanished: " + recordId));
	}

	private void parkMissing(DumpFileRecord record, String reason) {
		record.setPresentOnDisk(false);
		record.setWorkerId(null);
		record.setClaimedAt(null);
		record.setLastError(truncate(reason));
		record.setLastErrorAt(Instant.now());
		record.transitionTo(DumpStatus.MISSING);
	}

	private static BasicFileAttributes statOrNull(Path path) {
		try {
			return Files.readAttributes(path, BasicFileAttributes.class);
		}
		catch (IOException e) {
			IoFailure f = IoFailure.classify(e);
			if (f.kind() == IoFailure.Kind.NOT_FOUND) {
				return null;
			}
			throw IoFailure.wrap(e);
		}
	}

	private static String truncate(String s) {
		if (s == null) {
			return null;
		}
		return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN) + " …[truncated]";
	}

	// --- step result types ------------------------------------------------

	public record ChecksumWork(long recordId, String clientId, Path dumpPath, String reusableSha256) {

		public boolean canReuse() {
			return reusableSha256 != null && !reusableSha256.isBlank();
		}
	}

	public sealed interface ImportDecision {

		record Proceed(ImportRequest request) implements ImportDecision {
		}

		record Duplicate(Long originalId) implements ImportDecision {
		}

		record Aborted(String reason) implements ImportDecision {
		}
	}
}
