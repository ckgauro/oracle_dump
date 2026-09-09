package com.demo.oracle_dump.oracle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates the configured Oracle client executables once the context is ready (CLAUDE.md §13), so a
 * missing {@code impdp.exe} is discovered at startup rather than during the first 20 GB import.
 *
 * <p>Behaviour is config-driven: {@code validate-on-startup} toggles the check;
 * {@code fail-startup-on-missing-executable} decides whether a broken path aborts startup or is only
 * logged and surfaced through the health endpoint.
 */
@Component
public class OracleClientValidator {

	private static final Logger log = LoggerFactory.getLogger(OracleClientValidator.class);

	private final OracleImportProperties properties;
	private final AtomicReference<List<OracleExecutableCheck>> lastResult =
			new AtomicReference<>(List.of());

	public OracleClientValidator(OracleImportProperties properties) {
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void validateOnStartup() {
		if (!properties.getOracleClient().isValidateOnStartup()) {
			log.info("Oracle client validation disabled (oracle-import.oracle-client.validate-on-startup=false)");
			return;
		}
		List<OracleExecutableCheck> checks = validate();
		checks.forEach(this::logCheck);

		Mode mode = properties.getImporter().getMode();
		boolean realMode = mode == Mode.IMPDP || mode == Mode.IMP;
		List<OracleExecutableCheck> broken = checks.stream()
				.filter(OracleExecutableCheck::isBroken)
				.toList();

		if (!broken.isEmpty() && realMode && properties.getOracleClient().isFailStartupOnMissingExecutable()) {
			throw new IllegalStateException("Oracle client executable(s) unusable: "
					+ broken.stream().map(OracleExecutableCheck::name).collect(Collectors.joining(", ")));
		}
		if (!broken.isEmpty()) {
			log.warn("{} Oracle client executable(s) are configured but unusable: {}", broken.size(),
					broken.stream().map(OracleExecutableCheck::name).collect(Collectors.joining(", ")));
		}
	}

	/** Re-run the checks on demand (used by the health indicator). */
	public List<OracleExecutableCheck> validate() {
		var cfg = properties.getOracleClient();
		List<OracleExecutableCheck> checks = List.of(
				OracleExecutableCheck.inspect("impdp", cfg.getImpdpPath()),
				OracleExecutableCheck.inspect("imp", cfg.getImpPath()),
				OracleExecutableCheck.inspect("sqlplus", cfg.getSqlplusPath()));
		lastResult.set(checks);
		return checks;
	}

	public List<OracleExecutableCheck> lastResult() {
		return lastResult.get();
	}

	public Map<String, OracleExecutableCheck> lastResultByName() {
		return lastResult.get().stream()
				.collect(Collectors.toMap(OracleExecutableCheck::name, c -> c));
	}

	private void logCheck(OracleExecutableCheck c) {
		if (!c.isConfigured()) {
			log.info("Oracle client '{}': not configured", c.name());
		}
		else if (c.isUsable()) {
			log.info("Oracle client '{}': OK ({})", c.name(), c.configured());
		}
		else {
			log.warn("Oracle client '{}': UNUSABLE ({}) exists={} readable={} executable={}", c.name(),
					c.configured(), c.exists(), c.readable(), c.executable());
		}
	}
}
