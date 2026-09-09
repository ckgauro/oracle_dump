package com.demo.oracle_dump.scanner;

import java.nio.file.Path;

/** Immutable snapshot of one file on disk taken during a scan cycle. */
public record ScannedFile(
		String clientId,
		Path path,
		String fileName,
		long sizeBytes,
		long lastModifiedEpochMs) {

	public String absolutePath() {
		return path.toString();
	}
}
