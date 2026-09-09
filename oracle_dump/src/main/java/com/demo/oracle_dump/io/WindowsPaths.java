package com.demo.oracle_dump.io;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Path helpers that behave correctly for both local Windows drives and UNC network shares.
 *
 * <p>Rules enforced here (CLAUDE.md §4, §8, §12):
 * <ul>
 *   <li>always go through {@link java.nio.file.Path}/{@link java.nio.file.Files}, never string concat;</li>
 *   <li>accept forward slashes in configuration and preserve UNC roots ({@code \\server\share});</li>
 *   <li>normalise to an absolute path so downstream comparisons are stable.</li>
 * </ul>
 */
public final class WindowsPaths {

	private WindowsPaths() {
	}

	/**
	 * Convert a configured directory string into a normalised absolute {@link Path}.
	 *
	 * <p>Forward slashes are accepted everywhere. A leading {@code //} or {@code \\} is treated as a
	 * UNC root and preserved. On non-Windows JVMs (CI, local dev on macOS/Linux) a UNC-looking value
	 * is kept verbatim as a best effort so configuration still binds.
	 */
	public static Path toNormalizedPath(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("Path value is blank");
		}
		String value = raw.trim();
		boolean unc = value.startsWith("\\\\") || value.startsWith("//");

		if (unc) {
			// Preserve exactly two leading backslashes for the UNC root, forward slashes elsewhere.
			String body = value.substring(2).replace('/', '\\');
			Path p = Paths.get("\\\\" + body);
			return isWindows() ? p.normalize() : p;
		}

		Path p = Paths.get(value.replace('\\', '/'));
		return p.toAbsolutePath().normalize();
	}

	/** Resolve a child name against a base directory without any manual separator handling. */
	public static Path resolveChild(Path baseDirectory, String childName) {
		return baseDirectory.resolve(childName);
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}
}
