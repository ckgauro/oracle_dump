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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DumpFileRecord {

	@Setter(AccessLevel.NONE)
	@ToString.Include
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Setter(AccessLevel.NONE)
	@Version
	private long version;

	@Setter(AccessLevel.NONE)
	@ToString.Include
	@Column(name = "client_id", nullable = false, length = 128)
	private String clientId;

	@Setter(AccessLevel.NONE)
	@ToString.Include
	@Column(name = "file_name", nullable = false, length = 512)
	private String fileName;

	/** Absolute, normalised path (UNC-aware). Duplicate detection key together with {@link #clientId}. */
	@Setter(AccessLevel.NONE)
	@Column(name = "absolute_path", nullable = false, length = 2048)
	private String absolutePath;

	@ToString.Include(name = "size")
	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "last_modified_epoch_ms", nullable = false)
	private long lastModifiedEpochMs;

	@Column(name = "sha256", length = 64)
	private String sha256;

	@Setter(AccessLevel.NONE)
	@ToString.Include
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DumpStatus status = DumpStatus.DISCOVERED;

	// --- stability tracking -------------------------------------------------

	@Column(name = "stable_scan_count", nullable = false)
	private int stableScanCount;

	@Setter(AccessLevel.NONE)
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

	@ToString.Include(name = "attempts")
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

	public int incrementAttemptCount() {
		return ++this.attemptCount;
	}
}
