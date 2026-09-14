package com.oracle.checksum.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.checksum.domain.DumpFileRecord;
import com.oracle.checksum.domain.DumpFileStatus;
import com.oracle.checksum.repository.DumpFileRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The anti-double-pickup guard. A file is only ever processed by the worker
 * whose atomic conditional UPDATE actually flips it from DISCOVERED to
 * CLAIMED - a read-then-write here would race; the UPDATE...WHERE status =
 * DISCOVERED does not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecksumClaimService {

    private final DumpFileRecordRepository dumpFileRecordRepository;

    @Transactional(readOnly = true)
    public List<Long> discoveredIds() {
        return dumpFileRecordRepository.findByStatus(DumpFileStatus.DISCOVERED).stream()
                .map(DumpFileRecord::getId)
                .toList();
    }

    @Transactional
    public Optional<DumpFileRecord> claim(Long id, String claimedBy) {
        int updated = dumpFileRecordRepository.claim(id, claimedBy, Instant.now());
        if (updated == 0) {
            log.debug("Claim lost the race for dump_file_record {}", id);
            return Optional.empty();
        }
        return dumpFileRecordRepository.findById(id);
    }

    @Transactional
    public void markCompleted(Long id, String algorithm, String checksumValue) {
        dumpFileRecordRepository.findById(id).ifPresent(record -> {
            Instant completedAt = Instant.now();
            record.setChecksumAlgorithm(algorithm);
            record.setChecksumValue(checksumValue);
            record.setStatus(DumpFileStatus.COMPLETED);
            record.setCompletedAt(completedAt);
            record.setChecksumDurationMinutes(
                    BigDecimal.valueOf(Duration.between(record.getClaimedAt(), completedAt).toMillis())
                            .divide(BigDecimal.valueOf(60000), 8, RoundingMode.HALF_UP));
        });
    }

    @Transactional
    public void markFailed(Long id) {
        dumpFileRecordRepository.findById(id).ifPresent(record -> record.setStatus(DumpFileStatus.FAILED));
    }
}
