package com.demo.oracle_dump.processing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.io.IoFailure;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Streaming SHA-256 over a dump file. The whole file must be read (CLAUDE.md §18), so on a 20 GB UNC
 * dump this is 20 GB of network I/O — callers reuse a stored hash whenever path + size + mtime are
 * unchanged and only fall back to this when a fresh hash is genuinely required.
 */
@Slf4j
@Component
public class ChecksumService {

	private final OracleImportProperties properties;

	public ChecksumService(OracleImportProperties properties) {
		this.properties = properties;
	}

	public Result hash(Path file) {
		OracleImportProperties.Checksum cfg = properties.getChecksum();
		int bufferSize = Math.max(1, cfg.getBufferSizeMb()) * 1024 * 1024;
		MessageDigest digest = newDigest(cfg.getAlgorithm());

		Instant start = Instant.now();
		long bytes = 0;
		byte[] buffer = new byte[bufferSize];
		try (InputStream in = Files.newInputStream(file);
				DigestInputStream dis = new DigestInputStream(in, digest)) {
			int read;
			while ((read = dis.read(buffer)) != -1) {
				bytes += read;
			}
		}
		catch (IOException e) {
			throw IoFailure.wrap(e);
		}

		String hex = HexFormat.of().formatHex(digest.digest());
		Duration took = Duration.between(start, Instant.now());
		log.info("Checksum {} bytes={} in {} ({}) file={}", cfg.getAlgorithm(), bytes, took,
				throughput(bytes, took), file.getFileName());
		return new Result(hex, bytes, took);
	}

	private static MessageDigest newDigest(String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm, e);
		}
	}

	private static String throughput(long bytes, Duration took) {
		double seconds = Math.max(0.001, took.toNanos() / 1_000_000_000.0);
		double mbPerSec = (bytes / (1024.0 * 1024.0)) / seconds;
		return "%.1f MB/s".formatted(mbPerSec);
	}

	public record Result(String sha256Hex, long bytesRead, Duration elapsed) {
	}
}
