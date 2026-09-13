package com.oracle.checksum.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides the executor that runs file checksum work.
 *
 * Default is one virtual thread per file (checksum.executor.type=virtual):
 * the per-file bottleneck is blocking file I/O, so far more concurrent
 * reads can be in flight than there are physical cores. A fixed platform
 * thread pool (checksum.executor.type=fixed) is offered as a tuning
 * alternative for hardware where digest computation, not I/O, turns out
 * to dominate.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChecksumProperties.class)
@RequiredArgsConstructor
public class AsyncExecutorConfig {

    private final ChecksumProperties checksumProperties;

    @Bean(destroyMethod = "close")
    public ExecutorService checksumExecutor() {
        ChecksumProperties.Executor executorConfig = checksumProperties.getExecutor();
        if (executorConfig.getType() == ChecksumProperties.Executor.Type.FIXED) {
            log.info("Using fixed checksum executor with pool size {}", executorConfig.getFixedPoolSize());
            return Executors.newFixedThreadPool(executorConfig.getFixedPoolSize());
        }
        log.info("Using virtual-thread-per-task checksum executor");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
