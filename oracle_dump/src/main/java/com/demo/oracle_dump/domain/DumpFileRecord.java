package com.demo.oracle_dump.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Metadata for one dump file. This is the single source of truth for what has been seen, checksummed
 * and imported; the actual {@code impdp} output is written to a log file on disk and only its path is
 * stored here (CLAUDE.md §22).
 *
 * <p>Concurrency: {@link #version} gives optimistic locking so two workers (or two service instances)
 * cannot both drive the same record. Claiming is done with a status-guarded UPDATE in the repository.
 */
@Entity
@Table(name = "dump_file",
		uniqueConstraints = @UniqueConstraint(name = "uk_dump_client_path",
				columnNames = {"client_id", "absolute_path"}),
		indexes = {
				@Index(name = "ix_dump_status_eligible", columnList = "status,next_eligible_at"),
				@Index(name = "ix_dump_client_checksum", columnList = "client_id,sha256"),
				@Index(name = "ix_dump_in_flight", columnList = "status,claimed_at")
		})
public class DumpFileRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	private long version;

	@Column(name = "client_id", nullable = false, length = 128)
	private String clientId;

	@Column(name = "file_name", nullable = false, length = 512)
	private String fileName;

	/** Absolute, normalised path (UNC-aware). Duplicate detection key together with {@link #clientId}. */
	@Column(name = "absolute_path", nullable = false, length = 2048)
	private String absolutePath;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "last_modified_epoch_ms", nullable = false)
	private long lastModifiedEpochMs;

	@Column(name = "sha256", length = 64)
	private String sha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DumpStatus status = DumpStatus.DISCOVERED;

	// --- stability tracking -------------------------------------------------

	@Column(name = "stable_scan_count", nullable = false)
	private int stableScanCount;

	@Column(name = "first_seen_at", nullable = false)
	private Instant firstSeenAt = Instant.now();

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt = Instant.now();

	/** True on every scan where the file was found on disk; false marks it as vanished. */
	@Column(name = "present_on_disk", nullable = false)
	private boolean presentOnDisk = true;

	// --- dispatch / retry -------------------------------------------------

	@Column(name = "next_eligible_at", nullable = false)
	private Instant nextEligibleAt = Instant.now();

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "worker_id", length = 128)
	private String workerId;

	@Column(name = "claimed_at")
	private Instant claimedAt;

	// --- import result -------------------------------------------------

	@Column(name = "import_started_at")
	private Instant importStartedAt;

	@Column(name = "import_finished_at")
	private Instant importFinishedAt;

	@Column(name = "import_duration_ms")
	private Long importDurationMs;

	@Column(name = "import_log_path", length = 2048)
	private String importLogPath;

	@Column(name = "duplicate_of_id")
	private Long duplicateOfId;

	@Lob
	@Column(name = "last_error")
	private String lastError;

	@Column(name = "last_error_at")
	private Instant lastErrorAt;

	protected DumpFileRecord() {
	}

	public DumpFileRecord(String clientId, String fileName, String absolutePath, long sizeBytes,
			long lastModifiedEpochMs) {
		this.clientId = clientId;
		this.fileName = fileName;
		this.absolutePath = absolutePath;
		this.sizeBytes = sizeBytes;
		this.lastModifiedEpochMs = lastModifiedEpochMs;
	}

	/**
	 * Move to {@code target}, or throw {@link IllegalStateTransitionException} if the state machine in
	 * {@link DumpStatus} forbids it. Centralising this keeps illegal jumps out of the pipeline.
	 */
	public DumpFileRecord transitionTo(DumpStatus target) {
		if (!status.canTransitionTo(target)) {
			throw new IllegalStateTransitionException(id, status, target);
		}
		this.status = target;
		return this;
	}

	/** Snapshot of the on-disk identity used to decide whether a stored checksum can be reused. */
	public boolean matchesOnDisk(long size, long lastModifiedMs) {
		return this.sizeBytes == size && this.lastModifiedEpochMs == lastModifiedMs;
	}

	public boolean hasReusableChecksum(long size, long lastModifiedMs) {
		return sha256 != null && !sha256.isBlank() && matchesOnDisk(size, lastModifiedMs);
	}

	// --- getters / setters ------------------------------------------------

	public Long getId() {
		return id;
	}

	public long getVersion() {
		return version;
	}

	public String getClientId() {
		return clientId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getAbsolutePath() {
		return absolutePath;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public void setSizeBytes(long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public long getLastModifiedEpochMs() {
		return lastModifiedEpochMs;
	}

	public void setLastModifiedEpochMs(long lastModifiedEpochMs) {
		this.lastModifiedEpochMs = lastModifiedEpochMs;
	}

	public String getSha256() {
		return sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public DumpStatus getStatus() {
		return status;
	}

	public int getStableScanCount() {
		return stableScanCount;
	}

	public void setStableScanCount(int stableScanCount) {
		this.stableScanCount = stableScanCount;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public boolean isPresentOnDisk() {
		return presentOnDisk;
	}

	public void setPresentOnDisk(boolean presentOnDisk) {
		this.presentOnDisk = presentOnDisk;
	}

	public Instant getNextEligibleAt() {
		return nextEligibleAt;
	}

	public void setNextEligibleAt(Instant nextEligibleAt) {
		this.nextEligibleAt = nextEligibleAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public int incrementAttemptCount() {
		return ++this.attemptCount;
	}

	public String getWorkerId() {
		return workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public Instant getClaimedAt() {
		return claimedAt;
	}

	public void setClaimedAt(Instant claimedAt) {
		this.claimedAt = claimedAt;
	}

	public Instant getImportStartedAt() {
		return importStartedAt;
	}

	public void setImportStartedAt(Instant importStartedAt) {
		this.importStartedAt = importStartedAt;
	}

	public Instant getImportFinishedAt() {
		return importFinishedAt;
	}

	public void setImportFinishedAt(Instant importFinishedAt) {
		this.importFinishedAt = importFinishedAt;
	}

	public Long getImportDurationMs() {
		return importDurationMs;
	}

	public void setImportDurationMs(Long importDurationMs) {
		this.importDurationMs = importDurationMs;
	}

	public String getImportLogPath() {
		return importLogPath;
	}

	public void setImportLogPath(String importLogPath) {
		this.importLogPath = importLogPath;
	}

	public Long getDuplicateOfId() {
		return duplicateOfId;
	}

	public void setDuplicateOfId(Long duplicateOfId) {
		this.duplicateOfId = duplicateOfId;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public Instant getLastErrorAt() {
		return lastErrorAt;
	}

	public void setLastErrorAt(Instant lastErrorAt) {
		this.lastErrorAt = lastErrorAt;
	}

	@Override
	public String toString() {
		return "DumpFileRecord{id=" + id + ", clientId='" + clientId + "', fileName='" + fileName
				+ "', status=" + status + ", size=" + sizeBytes + ", attempts=" + attemptCount + "}";
	}
}
