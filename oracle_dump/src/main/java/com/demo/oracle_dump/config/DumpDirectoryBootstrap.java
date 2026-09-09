package com.demo.oracle_dump.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.demo.oracle_dump.config.ClientRegistry.ResolvedClient;
import com.demo.oracle_dump.io.WindowsPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * When {@code oracle-import.scanner.create-missing-directories=true} (dev only), creates any missing
 * <em>local</em> client dump directory and the import-log root. UNC shares are never created — a
 * missing share is an infrastructure concern and is simply logged (CLAUDE.md §29).
 */
@Component
public class DumpDirectoryBootstrap {

	private static final Logger log = LoggerFactory.getLogger(DumpDirectoryBootstrap.class);

	private final ClientRegistry clients;
	private final OracleImportProperties properties;

	public DumpDirectoryBootstrap(ClientRegistry clients, OracleImportProperties properties) {
		this.clients = clients;
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void ensureDirectories() {
		Path logRoot = WindowsPaths.toNormalizedPath(properties.getImporter().getImportLogRoot());
		createIfLocal(logRoot, "import-log root");

		if (!properties.getScanner().isCreateMissingDirectories()) {
			return;
		}
		for (ResolvedClient client : clients.all()) {
			createIfLocal(client.dumpDirectory(), "dump directory for client '" + client.id() + "'");
		}
	}

	private void createIfLocal(Path dir, String what) {
		String s = dir.toString();
		boolean unc = s.startsWith("\\\\") || s.startsWith("//");
		if (unc) {
			if (!Files.isDirectory(dir)) {
				log.warn("{} is a UNC path and does not exist right now: {}", what, dir);
			}
			return;
		}
		if (Files.isDirectory(dir)) {
			return;
		}
		try {
			Files.createDirectories(dir);
			log.info("Created {}: {}", what, dir);
		}
		catch (IOException e) {
			log.warn("Could not create {} ({}): {}", what, dir, e.getMessage());
		}
	}
}
