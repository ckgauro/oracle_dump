package com.oracle.checksum.scheduler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.oracle.checksum.config.ChecksumProperties;
import com.oracle.checksum.service.ChecksumComputationService;
import com.oracle.checksum.service.FileDiscoveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ChecksumProperties.class)
public class ChecksumScanScheduler {

    private final FileDiscoveryService fileDiscoveryService;
    private final ChecksumComputationService checksumComputationService;

    @Scheduled(cron = "${checksum.scan.cron}")
    public void scan() {
        log.debug("Starting checksum scan cycle");
        fileDiscoveryService.discover();
        checksumComputationService.processDiscoveredFiles();
    }
}
