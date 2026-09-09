package com.demo.oracle_dump.processing;

import java.util.concurrent.ThreadPoolExecutor;

import com.demo.oracle_dump.config.OracleImportProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool for import work. Bounded on purpose: an Oracle Data Pump import is heavy and largely
 * out-of-process, so this is CPU/IO-bound work that must be capped — not a job for unbounded virtual
 * threads. The queue is bounded to {@code maxWorkers}; the dispatcher only ever claims as many
 * records as there is free capacity, and {@link ThreadPoolExecutor.AbortPolicy} makes an accidental
 * over-submit fail loudly (the record is then returned to the queue) rather than pile up.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ProcessingConfig {

	public static final String IMPORT_EXECUTOR = "importTaskExecutor";

	@Bean(name = IMPORT_EXECUTOR, destroyMethod = "shutdown")
	public ThreadPoolTaskExecutor importTaskExecutor(OracleImportProperties properties) {
		int workers = properties.getProcessing().getMaxWorkers();
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("dump-import-");
		executor.setCorePoolSize(workers);
		executor.setMaxPoolSize(workers);
		executor.setQueueCapacity(workers);
		executor.setAllowCoreThreadTimeOut(false);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(
				(int) properties.getProcessing().getShutdownGracePeriod().toSeconds());
		executor.initialize();
		return executor;
	}
}
