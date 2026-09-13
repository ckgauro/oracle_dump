package com.oracle.checksum.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.oracle.checksum.domain.DumpFileRecord;
import com.oracle.checksum.domain.DumpFileStatus;

public interface DumpFileRecordRepository extends JpaRepository<DumpFileRecord, Long> {

    boolean existsByClient_IdAndFilePath(Long clientId, String filePath);

    List<DumpFileRecord> findByStatus(DumpFileStatus status);

    /**
     * Atomic claim: succeeds (returns 1) only if the row is still DISCOVERED.
     * This is the guard against two threads/instances picking up the same file -
     * a conditional UPDATE, not a read-then-write.
     */
    @Modifying
    @Query("update DumpFileRecord d set d.status = com.oracle.checksum.domain.DumpFileStatus.CLAIMED, "
            + "d.claimedBy = :claimedBy, d.claimedAt = :claimedAt "
            + "where d.id = :id and d.status = com.oracle.checksum.domain.DumpFileStatus.DISCOVERED")
    int claim(@Param("id") Long id, @Param("claimedBy") String claimedBy, @Param("claimedAt") Instant claimedAt);
}
