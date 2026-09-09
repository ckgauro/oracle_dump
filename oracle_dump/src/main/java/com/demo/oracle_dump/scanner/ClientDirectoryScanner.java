package com.demo.oracle_dump.scanner;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import com.demo.oracle_dump.config.ClientRegistry.ResolvedClient;
import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.domain.DumpFileRecord;
import com.demo.oracle_dump.domain.DumpFileRepository;
import com.demo.oracle_dump.domain.DumpStatus;
import com.demo.oracle_dump.io.IoFailure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scans exactly one client's dump directory inside its own transaction, so a failure on one bad UNC
 * share never rolls back progress for the other clients (CLAUDE.md §29). All filesystem access goes
 * through NIO {@link Files}/{@link DirectoryStream} (CLAUDE.md §4, §8).
 */
@Component
public class ClientDirectoryScanner {

	private static final Logger log = LoggerFactory.getLogger(ClientDirectoryScanner.class);

	private final DumpFileRepository repository;
	private final FileStabilityChecker stabilityChecker;
	private final OracleImportProperties properties;

	public ClientDirectoryScanner(DumpFileRepository repository, FileStabilityChecker stabilityChecker,
			OracleImportProperties properties) {
		this.repository = repository;
		this.stabilityChecker = stabilityChecker;
		this.properties = properties;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ScanSummary scan(ResolvedClient client) {
		Path dir = client.dumpDirectory();
		if (!Files.isDirectory(dir) || !Files.isReadable(dir)) {
			log.warn("Client '{}' dump directory not available: {}", client.id(), dir);
			return ScanSummary.unavailable();
		}

		Instant now = Instant.now();
		List<ScannedFile> found = listDumpFiles(client.id(), dir);
		Set<String> seenPaths = new HashSet<>();
		ScanSummary summary = ScanSummary.empty();

		for (ScannedFile file : found) {
			seenPaths.add(file.absolutePath());
			summary = summary.plus(upsert(client, file, now));
		}

		int vanished = markVanished(client.id(), seenPaths);

		if (log.isDebugEnabled()) {
			log.debug("Scan client='{}' dir={} found={} new={} eligible={} vanished={}", client.id(),
					dir, found.size(), summary.discovered(), summary.eligible(), vanished);
		}
		return summary.withVanished(vanished);
	}

	private int markVanished(String clientId, Set<String> seenPaths) {
		return repository.findByClientId(clientId).stream()
				.filter(DumpFileRecord::isPresentOnDisk)
				.filter(r -> !seenPaths.contains(r.getAbsolutePath()))
				.mapToInt(r -> {
					r.setPresentOnDisk(false);
					if (r.getStatus() == DumpStatus.DISCOVERED || r.getStatus() == DumpStatus.STABILIZING
							|| r.getStatus() == DumpStatus.PENDING_IMPORT) {
						r.transitionTo(DumpStatus.MISSING);
					}
					return 1;
				})
				.sum();
	}

	private ScanSummary upsert(ResolvedClient client, ScannedFile file, Instant now) {
		DumpFileRecord record = repository
				.findByClientIdAndAbsolutePath(client.id(), file.absolutePath())
				.orElse(null);

		if (record == null) {
			DumpFileRecord fresh = new DumpFileRecord(client.id(), file.fileName(),
					file.absolutePath(), file.sizeBytes(), file.lastModifiedEpochMs());
			fresh.setStableScanCount(1);
			fresh.setLastSeenAt(now);
			fresh.transitionTo(DumpStatus.STABILIZING);
			try {
				repository.saveAndFlush(fresh);
				log.info("Discovered dump: client='{}' file='{}' size={}", client.id(), file.fileName(),
						file.sizeBytes());
				return ScanSummary.of(1, 0, 0);
			}
			catch (DataIntegrityViolationException raced) {
				record = repository.findByClientIdAndAbsolutePath(client.id(), file.absolutePath())
						.orElseThrow(() -> raced);
			}
		}

		record.setLastSeenAt(now);
		record.setPresentOnDisk(true);

		boolean changedOnDisk = !record.matchesOnDisk(file.sizeBytes(), file.lastModifiedEpochMs());
		if ((record.getStatus().isTerminal() || record.getStatus() == DumpStatus.FAILED
				|| record.getStatus() == DumpStatus.MISSING) && changedOnDisk) {
			reopen(record, file);
			return ScanSummary.of(1, 0, 0);
		}

		if (record.getStatus() != DumpStatus.DISCOVERED && record.getStatus() != DumpStatus.STABILIZING) {
			return ScanSummary.empty();
		}

		FileStabilityChecker.Assessment a = stabilityChecker.assess(record, file, now);
		record.setSizeBytes(file.sizeBytes());
		record.setLastModifiedEpochMs(file.lastModifiedEpochMs());
		record.setStableScanCount(a.stableScanCount());

		if (a.eligible()) {
			record.setNextEligibleAt(now);
			record.transitionTo(DumpStatus.PENDING_IMPORT);
			log.info("Dump eligible for import: client='{}' file='{}' size={}", client.id(),
					file.fileName(), file.sizeBytes());
			return ScanSummary.of(0, 1, 0);
		}
		record.transitionTo(DumpStatus.STABILIZING);
		return ScanSummary.empty();
	}

	private void reopen(DumpFileRecord record, ScannedFile file) {
		record.transitionTo(DumpStatus.DISCOVERED);
		record.setSizeBytes(file.sizeBytes());
		record.setLastModifiedEpochMs(file.lastModifiedEpochMs());
		record.setSha256(null);
		record.setStableScanCount(1);
		record.setAttemptCount(0);
		record.setWorkerId(null);
		record.setClaimedAt(null);
		record.setDuplicateOfId(null);
		record.setImportStartedAt(null);
		record.setImportFinishedAt(null);
		record.setImportDurationMs(null);
		record.transitionTo(DumpStatus.STABILIZING);
		log.info("Re-opened changed dump: client='{}' file='{}'", record.getClientId(),
				record.getFileName());
	}

	private List<ScannedFile> listDumpFiles(String clientId, Path dir) {
		Predicate<String> isDump = name -> endsWithAny(name, properties.getScanner().getDumpSuffixes());
		Predicate<String> isIgnored =
				name -> endsWithAny(name, properties.getScanner().getIgnoreSuffixes());

		List<ScannedFile> result = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, entry -> {
			String lower = entry.getFileName().toString().toLowerCase(Locale.ROOT);
			return isDump.test(lower) && !isIgnored.test(lower) && Files.isRegularFile(entry);
		})) {
			for (Path entry : stream) {
				try {
					BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
					result.add(new ScannedFile(clientId, entry.toAbsolutePath().normalize(),
							entry.getFileName().toString(), attrs.size(),
							attrs.lastModifiedTime().toMillis()));
				}
				catch (IOException perFile) {
					log.debug("Skipping unreadable entry {} : {}", entry, perFile.getMessage());
				}
			}
		}
		catch (IOException e) {
			throw IoFailure.wrap(e);
		}
		return result;
	}

	private static boolean endsWithAny(String lowerName, List<String> suffixes) {
		return suffixes.stream().anyMatch(s -> lowerName.endsWith(s.toLowerCase(Locale.ROOT)));
	}
}
