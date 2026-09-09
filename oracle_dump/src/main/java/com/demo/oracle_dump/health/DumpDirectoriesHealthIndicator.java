package com.demo.oracle_dump.health;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.demo.oracle_dump.config.ClientRegistry;
import com.demo.oracle_dump.config.ClientRegistry.ResolvedClient;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Lightweight availability check for every enabled client's dump directory (CLAUDE.md §28).
 *
 * <p>Only {@code isDirectory} + {@code isReadable} are probed — never a full scan — and the result is
 * cached for a few seconds so hammering {@code /actuator/health} cannot turn into a storm of UNC
 * round-trips. A single unreachable share reports {@code OUT_OF_SERVICE}, not {@code DOWN}, because a
 * temporarily missing share is expected and recoverable (CLAUDE.md §29).
 */
@Component("dumpDirectories")
public class DumpDirectoriesHealthIndicator implements HealthIndicator {

	private static final Duration CACHE_TTL = Duration.ofSeconds(10);

	private final ClientRegistry clients;
	private final AtomicReference<Cached> cache = new AtomicReference<>();

	public DumpDirectoriesHealthIndicator(ClientRegistry clients) {
		this.clients = clients;
	}

	@Override
	public Health health() {
		Cached cached = cache.get();
		if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
			return cached.health();
		}
		Health fresh = probe();
		cache.set(new Cached(Instant.now(), fresh));
		return fresh;
	}

	private Health probe() {
		Map<String, Object> details = new LinkedHashMap<>();
		boolean anyDown = false;
		for (ResolvedClient client : clients.enabled()) {
			Path dir = client.dumpDirectory();
			boolean up = safeCheck(dir);
			anyDown |= !up;
			details.put(client.id(), (up ? "UP" : "DOWN") + " (" + dir + ")");
		}
		if (clients.enabled().isEmpty()) {
			return Health.status(Status.UNKNOWN).withDetail("reason", "no enabled clients").build();
		}
		Status status = anyDown ? Status.OUT_OF_SERVICE : Status.UP;
		return Health.status(status).withDetails(details).build();
	}

	private static boolean safeCheck(Path dir) {
		try {
			return Files.isDirectory(dir) && Files.isReadable(dir);
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	private record Cached(Instant at, Health health) {
	}
}
