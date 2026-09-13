package com.oracle.checksum.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.oracle.checksum.config.ChecksumProperties;
import com.oracle.checksum.domain.DumpFileRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes checksums in parallel. Each task first claims its file via
 * ChecksumClaimService; only the thread that wins the claim reads and
 * hashes the file. Each task uses its own MessageDigest instance - digests
 * are not thread-safe to share - and streams the file through a reused
 * direct ByteBuffer instead of loading it whole into heap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecksumComputationService {

    private static final int CHUNK_SIZE = 8 * 1024 * 1024;

    private final ChecksumClaimService checksumClaimService;
    private final ExecutorService checksumExecutor;
    private final ChecksumProperties checksumProperties;

    public void processDiscoveredFiles() {
        List<Long> candidateIds = checksumClaimService.discoveredIds();
        if (candidateIds.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> tasks = candidateIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> processOne(id), checksumExecutor))
                .toList();

        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    }

    private void processOne(Long id) {
        String workerId = Thread.currentThread().toString();
        Optional<DumpFileRecord> claimed = checksumClaimService.claim(id, workerId);
        if (claimed.isEmpty()) {
            return;
        }

        DumpFileRecord record = claimed.get();
        String algorithm = checksumProperties.getAlgorithm();
        try {
            String checksum = computeChecksum(Path.of(record.getFilePath()), algorithm);
            checksumClaimService.markCompleted(id, algorithm, checksum);
            log.info("Checksummed {} ({}) -> {}", record.getFilePath(), algorithm, checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to checksum {}", record.getFilePath(), e);
            checksumClaimService.markFailed(id);
        }
    }

    private String computeChecksum(Path file, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK_SIZE);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}
