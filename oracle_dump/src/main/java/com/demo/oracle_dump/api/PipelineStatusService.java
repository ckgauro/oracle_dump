package com.demo.oracle_dump.api;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRecord;
import com.demo.oracle_dump.domain.DumpFileRepository;
import com.demo.oracle_dump.domain.DumpStatus;
import com.demo.oracle_dump.lifecycle.PipelineState;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read model + a couple of operator actions over the dump metadata. */
@Service
public class PipelineStatusService {

	private final DumpFileRepository repository;
	private final OracleImportProperties properties;
	private final PipelineState pipelineState;

	public PipelineStatusService(DumpFileRepository repository, OracleImportProperties properties,
			PipelineState pipelineState) {
		this.repository = repository;
		this.properties = properties;
		this.pipelineState = pipelineState;
	}

	@Transactional(readOnly = true)
	public PipelineStatus status() {
		Map<DumpStatus, Long> counts = new EnumMap<>(DumpStatus.class);
		for (DumpStatus s : DumpStatus.values()) {
			counts.put(s, repository.countByStatus(s));
		}
		return new PipelineStatus(
				pipelineState.isAcceptingWork(),
				properties.getImporter().getMode().name(),
				properties.getProcessing().getMaxWorkers(),
				counts);
	}

	@Transactional(readOnly = true)
	public List<DumpView> recent(DumpStatus status, int limit) {
		List<DumpFileRecord> records = (status == null)
				? repository.findAll(org.springframework.data.domain.PageRequest.of(0, limit,
						org.springframework.data.domain.Sort.by("lastSeenAt").descending())).getContent()
				: repository.findByStatusOrderByLastSeenAtDesc(status, Limit.of(limit));
		return records.stream().map(DumpView::from).toList();
	}

	/** Operator action: push a FAILED (or MISSING) record back into the queue for another attempt. */
	@Transactional
	public DumpView retry(long id) {
		DumpFileRecord record = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("No dump record " + id));
		if (record.getStatus() != DumpStatus.FAILED && record.getStatus() != DumpStatus.MISSING) {
			throw new IllegalStateException("Record " + id + " is " + record.getStatus()
					+ "; only FAILED/MISSING can be retried");
		}
		record.setAttemptCount(0);
		record.setLastError(null);
		record.setNextEligibleAt(java.time.Instant.now());
		record.transitionTo(DumpStatus.PENDING_IMPORT);
		return DumpView.from(record);
	}

	public record PipelineStatus(boolean acceptingWork, String importerMode, int maxWorkers,
			Map<DumpStatus, Long> countsByStatus) {
	}

	public record DumpView(Long id, String clientId, String fileName, String status, long sizeBytes,
			String sha256, int attemptCount, String lastError, String importLogPath,
			Long importDurationMs) {

		static DumpView from(DumpFileRecord r) {
			return new DumpView(r.getId(), r.getClientId(), r.getFileName(), r.getStatus().name(),
					r.getSizeBytes(), r.getSha256(), r.getAttemptCount(), r.getLastError(),
					r.getImportLogPath(), r.getImportDurationMs());
		}
	}
}
