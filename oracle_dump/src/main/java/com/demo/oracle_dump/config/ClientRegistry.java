package com.demo.oracle_dump.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import com.demo.oracle_dump.io.WindowsPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves the configured clients once at startup: validates that client IDs are unique, normalises
 * each dump directory to an absolute {@link Path} (UNC-aware), and creates one fair {@link Semaphore}
 * per client to enforce {@code maxParallelImports}.
 */
@Component
public class ClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(ClientRegistry.class);

	private final Map<String, ResolvedClient> clients = new LinkedHashMap<>();

	public ClientRegistry(OracleImportProperties properties) {
		for (ClientDefinition def : properties.getClients()) {
			String id = def.getClientId();
			if (clients.containsKey(id)) {
				throw new IllegalStateException("Duplicate oracle-import client-id: " + id);
			}
			Path dir = WindowsPaths.toNormalizedPath(def.getDumpDirectory());
			clients.put(id, new ResolvedClient(def, dir, new Semaphore(def.getMaxParallelImports(), true)));
			log.info("Registered client '{}' enabled={} dir={} targetSchema={} maxParallelImports={}",
					id, def.isEnabled(), dir, def.getTargetSchema(), def.getMaxParallelImports());
		}
		if (clients.isEmpty()) {
			log.warn("No oracle-import clients configured; scanner will have nothing to do");
		}
	}

	public List<ResolvedClient> all() {
		return List.copyOf(clients.values());
	}

	public List<ResolvedClient> enabled() {
		return clients.values().stream().filter(c -> c.definition().isEnabled()).toList();
	}

	public Optional<ResolvedClient> find(String clientId) {
		return Optional.ofNullable(clients.get(clientId));
	}

	public ResolvedClient require(String clientId) {
		ResolvedClient c = clients.get(clientId);
		if (c == null) {
			throw new IllegalArgumentException("Unknown clientId: " + clientId);
		}
		return c;
	}

	/** Immutable per-client runtime view. */
	public record ResolvedClient(ClientDefinition definition, Path dumpDirectory, Semaphore importPermits) {

		public String id() {
			return definition.getClientId();
		}
	}
}
