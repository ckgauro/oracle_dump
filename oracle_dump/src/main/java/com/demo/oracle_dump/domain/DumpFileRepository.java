package com.demo.oracle_dump.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DumpFileRepository extends JpaRepository<DumpFileRecord, Long> {

	Optional<DumpFileRecord> findByClientIdAndAbsolutePath(String clientId, String absolutePath);

	List<DumpFileRecord> findByClientId(String clientId);

	/**
	 * Candidates the dispatcher may claim: eligible now, not terminal, not in flight. Ordered oldest
	 * first for rough FIFO fairness.
	 */
	@Query("""
			select r from DumpFileRecord r
			where r.status = com.demo.oracle_dump.domain.DumpStatus.PENDING_IMPORT
			  and r.nextEligibleAt <= :now
			order by r.nextEligibleAt asc, r.id asc
			""")
	List<DumpFileRecord> findClaimable(@Param("now") Instant now, Limit limit);

	/**
	 * Atomic, status-guarded claim. Returns 1 if this caller won the race for the record, 0 otherwise.
	 * Safe across threads and across service instances.
	 */
	@Modifying
	@Query("""
			update DumpFileRecord r
			   set r.status = com.demo.oracle_dump.domain.DumpStatus.QUEUED,
			       r.workerId = :workerId,
			       r.claimedAt = :now,
			       r.version = r.version + 1
			 where r.id = :id
			   and r.status = com.demo.oracle_dump.domain.DumpStatus.PENDING_IMPORT
			""")
	int claim(@Param("id") Long id, @Param("workerId") String workerId, @Param("now") Instant now);

	/** Reset records abandoned by a crashed worker/instance back to the queue. */
	@Modifying
	@Query("""
			update DumpFileRecord r
			   set r.status = com.demo.oracle_dump.domain.DumpStatus.PENDING_IMPORT,
			       r.workerId = null,
			       r.claimedAt = null,
			       r.nextEligibleAt = :now,
			       r.version = r.version + 1
			 where r.status in (com.demo.oracle_dump.domain.DumpStatus.QUEUED,
			                    com.demo.oracle_dump.domain.DumpStatus.CHECKSUMMING,
			                    com.demo.oracle_dump.domain.DumpStatus.IMPORTING)
			   and r.claimedAt < :threshold
			""")
	int reclaimStale(@Param("now") Instant now, @Param("threshold") Instant threshold);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from DumpFileRecord r where r.id = :id")
	Optional<DumpFileRecord> findByIdForUpdate(@Param("id") Long id);

	/** Prior successful (or in-progress) import of the same content for the same client. */
	@Query("""
			select r from DumpFileRecord r
			where r.clientId = :clientId
			  and r.sha256 = :sha256
			  and r.id <> :selfId
			  and r.status in (com.demo.oracle_dump.domain.DumpStatus.IMPORTED,
			                   com.demo.oracle_dump.domain.DumpStatus.IMPORTING,
			                   com.demo.oracle_dump.domain.DumpStatus.CHECKSUMMING)
			order by r.id asc
			""")
	List<DumpFileRecord> findChecksumSiblings(@Param("clientId") String clientId,
			@Param("sha256") String sha256, @Param("selfId") Long selfId);

	long countByStatus(DumpStatus status);

	List<DumpFileRecord> findByStatusOrderByLastSeenAtDesc(DumpStatus status, Limit limit);
}
