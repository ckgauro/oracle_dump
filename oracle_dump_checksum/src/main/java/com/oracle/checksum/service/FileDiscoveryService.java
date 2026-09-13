package com.oracle.checksum.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.checksum.domain.ClientFileLocation;
import com.oracle.checksum.domain.DumpFileRecord;
import com.oracle.checksum.domain.DumpFileStatus;
import com.oracle.checksum.repository.DumpFileRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Walks each active client_file_location.base_path and inserts a DISCOVERED
 * row for every matching file not already present in dump_file_record for
 * that client+path. A file already known (any status, including COMPLETED)
 * is never re-inserted - that is what makes "already picked up" durable
 * across restarts instead of an in-memory fact.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscoveryService {

    private final ClientConfigService clientConfigService;
    private final DumpFileRecordRepository dumpFileRecordRepository;

    public void discover() {
        for (ClientFileLocation location : clientConfigService.activeLocations()) {
            discoverLocation(location);
        }
    }

    private void discoverLocation(ClientFileLocation location) {
        Path baseDir = Path.of(location.getBasePath());
        if (!Files.isDirectory(baseDir)) {
            log.warn("Skipping client {} location {} - not a directory", location.getClient().getCode(), baseDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, location.resolveFilePattern())) {
            for (Path candidate : stream) {
                if (Files.isRegularFile(candidate)) {
                    registerIfNew(location, candidate);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan client {} location {}", location.getClient().getCode(), baseDir, e);
        }
    }

    @Transactional
    void registerIfNew(ClientFileLocation location, Path file) {
        Long clientId = location.getClient().getId();
        String absolutePath = file.toAbsolutePath().toString();

        if (dumpFileRecordRepository.existsByClient_IdAndFilePath(clientId, absolutePath)) {
            return;
        }

        try {
            long size = Files.size(file);
            Instant lastModified = Files.getLastModifiedTime(file).toInstant().atZone(ZoneOffset.UTC).toInstant();

            DumpFileRecord record = DumpFileRecord.builder()
                    .client(location.getClient())
                    .filePath(absolutePath)
                    .fileSize(size)
                    .lastModified(lastModified)
                    .status(DumpFileStatus.DISCOVERED)
                    .build();

            dumpFileRecordRepository.save(record);
            log.info("Discovered dump file for client {}: {}", location.getClient().getCode(), absolutePath);
        } catch (IOException e) {
            log.warn("Failed to read metadata for candidate file {}", absolutePath, e);
        }
    }
}
