package com.demo.oracle_dump;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Oracle dump import service.
 *
 * <p>Scans configured client directories (local Windows drives and UNC shares) for {@code .dmp}
 * files, waits until each file is fully copied, verifies it by SHA-256, de-duplicates by
 * client + content, and runs a bounded pool of import workers. Designed to run as a Windows Service;
 * see {@code CLAUDE.md} for the full runtime contract.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OracleDumpApplication {

	public static void main(String[] args) {
		SpringApplication.run(OracleDumpApplication.class, args);
	}

}
