package com.demo.oracle_dump.importer;

import java.util.List;
import java.util.stream.Collectors;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;

/**
 * Wires the {@link DumpImporter} that matches {@code oracle-import.importer.mode}. The rest of the
 * pipeline depends only on the {@code @Primary} {@link DumpImporter}, so switching modes is a
 * config-only change.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class ImporterConfig {

	@Bean
	OracleDataPumpImporter impdpImporter(OracleImportProperties properties) {
		return new OracleDataPumpImporter(properties, Mode.IMPDP);
	}

	@Bean
	OracleDataPumpImporter impImporter(OracleImportProperties properties) {
		return new OracleDataPumpImporter(properties, Mode.IMP);
	}

	/**
	 * Choose the importer whose {@link DumpImporter#mode()} equals the configured mode. Fails fast if
	 * nothing matches so a typo in config cannot silently fall back to mock in production.
	 */
	@Bean
	@Primary
	DumpImporter activeDumpImporter(OracleImportProperties properties, List<DumpImporter> candidates) {
		Mode wanted = properties.getImporter().getMode();
		DumpImporter chosen = candidates.stream()
				.filter(i -> i.mode() == wanted)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"No DumpImporter for mode " + wanted + "; available: "
								+ candidates.stream().map(i -> i.mode().name())
										.collect(Collectors.joining(", "))));
		log.info("Active dump importer: {} (mode={})", chosen.getClass().getSimpleName(), wanted);
		return chosen;
	}
}
