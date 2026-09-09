package com.demo.oracle_dump.oracle;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Result of validating one configured Oracle client executable (CLAUDE.md §13).
 *
 * @param name       logical name ({@code impdp}, {@code imp}, {@code sqlplus})
 * @param configured raw configured path, or {@code null} if not set
 * @param exists     the file exists
 * @param readable   the service account can read it
 * @param executable the service account can execute it
 */
public record OracleExecutableCheck(String name, String configured, boolean exists, boolean readable,
		boolean executable) {

	public static OracleExecutableCheck notConfigured(String name) {
		return new OracleExecutableCheck(name, null, false, false, false);
	}

	public static OracleExecutableCheck inspect(String name, String configuredPath) {
		if (configuredPath == null || configuredPath.isBlank()) {
			return notConfigured(name);
		}
		Path p = Path.of(configuredPath).toAbsolutePath().normalize();
		boolean exists = Files.exists(p);
		return new OracleExecutableCheck(name, configuredPath, exists, exists && Files.isReadable(p),
				exists && Files.isExecutable(p));
	}

	public boolean isConfigured() {
		return configured != null && !configured.isBlank();
	}

	public boolean isUsable() {
		return exists && readable && executable;
	}

	public boolean isBroken() {
		return isConfigured() && !isUsable();
	}
}
