package com.demo.oracle_dump.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

/**
 * Root configuration for the Oracle dump import pipeline.
 *
 * <p>Bound from the {@code oracle-import.*} tree in {@code application.yaml}. The design targets
 * Windows Server: dump directories may be local drives ({@code D:/OracleDumps/ClientA}) or UNC
 * shares ({@code //nas01/oracle-dumps/ClientA}). See {@code CLAUDE.md} for the full runtime contract.
 */
@Validated
@Getter
@ConfigurationProperties(prefix = "oracle-import")
public class OracleImportProperties {

	@Valid
	private final Scanner scanner = new Scanner();

	@Valid
	private final Processing processing = new Processing();

	@Valid
	private final Checksum checksum = new Checksum();

	@Valid
	private final OracleClient oracleClient = new OracleClient();

	@Valid
	private final Importer importer = new Importer();

	@Setter
	private List<@Valid ClientDefinition> clients = new ArrayList<>();

	/** Directory scanning and file-stability detection. */
	@Getter
	@Setter
	public static class Scanner {

		private boolean enabled = true;

		/** Delay between the end of one scan cycle and the start of the next. */
		@NotNull
		private Duration interval = Duration.ofSeconds(30);

		/**
		 * A file is only eligible once it has been unchanged for at least this long (compared against
		 * its last-modified timestamp). Guards against Windows/UNC copies that expose a partial file.
		 */
		@NotNull
		private Duration stableFileCheckDelay = Duration.ofSeconds(30);

		/** Number of consecutive scans with identical size + last-modified before a file is eligible. */
		@Min(2)
		private int stableScansRequired = 2;

		/** Filename suffixes (lower-cased) that are always ignored while copying is in progress. */
		private List<String> ignoreSuffixes = new ArrayList<>(List.of(".part", ".tmp", ".partial", ".copying"));

		/** Filename suffixes (lower-cased) treated as importable dump files. */
		private List<String> dumpSuffixes = new ArrayList<>(List.of(".dmp"));

		/**
		 * Dev convenience: create local (non-UNC) client directories at startup if missing. Never
		 * attempts to create UNC shares. Leave {@code false} in production.
		 */
		private boolean createMissingDirectories = false;
	}

	/** Worker pool and retry behaviour. */
	@Getter
	@Setter
	public static class Processing {

		private boolean enabled = true;

		/** Total worker threads across all clients. Per-client limits still apply on top of this. */
		@Min(1)
		private int maxWorkers = 4;

		/** How often the dispatcher polls the metadata DB for eligible work. */
		@NotNull
		private Duration pollInterval = Duration.ofSeconds(10);

		/**
		 * A record stuck in an in-flight state (QUEUED/CHECKSUMMING/IMPORTING) for longer than this is
		 * considered abandoned (crash, killed service) and is reset to be retried.
		 */
		@NotNull
		private Duration staleProcessingTimeout = Duration.ofHours(2);

		/** Maximum import attempts before a dump is parked in FAILED. */
		@Min(1)
		private int maxAttempts = 5;

		/** Backoff base; attempt n waits {@code retryBackoff * 2^(n-1)}, capped by {@link #retryBackoffMax}. */
		@NotNull
		private Duration retryBackoff = Duration.ofMinutes(1);

		@NotNull
		private Duration retryBackoffMax = Duration.ofHours(1);

		/** Max number of records claimed per dispatcher tick. */
		@Min(1)
		private int dispatchBatchSize = 50;

		/** Grace period the dispatcher waits for in-flight imports on shutdown before force-stopping. */
		@NotNull
		private Duration shutdownGracePeriod = Duration.ofMinutes(2);
	}

	/** SHA-256 streaming checksum settings. */
	@Getter
	@Setter
	public static class Checksum {

		@NotBlank
		private String algorithm = "SHA-256";

		/** Streaming read buffer. Larger buffers help on high-latency UNC shares. */
		@Positive
		private int bufferSizeMb = 8;
	}

	/** Location of the Oracle client utilities and the environment they need. Windows {@code .exe}s. */
	@Getter
	@Setter
	public static class OracleClient {

		private String impdpPath;

		private String impPath;

		private String sqlplusPath;

		private String oracleHome;

		private String tnsAdmin;

		/** Fail application startup if a configured executable is missing/unreadable. */
		private boolean validateOnStartup = true;

		/** When {@code true}, missing executables abort startup; when {@code false} they only warn. */
		private boolean failStartupOnMissingExecutable = false;
	}

	/** Selects which {@code DumpImporter} implementation is active. */
	@Getter
	@Setter
	public static class Importer {

		public enum Mode {
			/** No Oracle contact; simulates a successful import. Safe default for dev/CI. */
			MOCK,
			/** Real {@code impdp.exe} execution. Not implemented yet (see CLAUDE.md section 10). */
			IMPDP,
			/** Real legacy {@code imp.exe} execution. Not implemented yet. */
			IMP
		}

		@NotNull
		private Mode mode = Mode.MOCK;

		/** Simulated import duration for {@link Mode#MOCK}. */
		@NotNull
		private Duration mockDuration = Duration.ofSeconds(2);

		/** Root directory for per-client import logs. Keep on local disk, not a share. */
		@NotBlank
		private String importLogRoot = "./logs/imports";

		/** Hard ceiling on a single import; exceeding it kills the child process and fails the attempt. */
		@NotNull
		private Duration importTimeout = Duration.ofHours(6);
	}
}
