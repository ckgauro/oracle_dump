package com.demo.oracle_dump.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;
import com.demo.oracle_dump.oracle.OracleClientValidator;
import com.demo.oracle_dump.oracle.OracleExecutableCheck;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Surfaces the state of the configured Oracle client executables (CLAUDE.md §13). In {@code mock}
 * mode a broken/absent path is informational ({@code UP}); in a real import mode it is
 * {@code OUT_OF_SERVICE} so operators see it before scheduling imports.
 */
@Component("oracleClient")
public class OracleClientHealthIndicator implements HealthIndicator {

	private final OracleClientValidator validator;
	private final OracleImportProperties properties;

	public OracleClientHealthIndicator(OracleClientValidator validator,
			OracleImportProperties properties) {
		this.validator = validator;
		this.properties = properties;
	}

	@Override
	public Health health() {
		List<OracleExecutableCheck> checks = validator.validate();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("importerMode", properties.getImporter().getMode());
		for (OracleExecutableCheck c : checks) {
			details.put(c.name(), !c.isConfigured() ? "not-configured"
					: c.isUsable() ? "OK (" + c.configured() + ")"
					: "UNUSABLE (" + c.configured() + ") exists=" + c.exists() + " readable=" + c.readable()
							+ " executable=" + c.executable());
		}

		Mode mode = properties.getImporter().getMode();
		boolean realMode = mode == Mode.IMPDP || mode == Mode.IMP;
		boolean anyBroken = checks.stream().anyMatch(OracleExecutableCheck::isBroken);
		Status status = (realMode && anyBroken) ? Status.OUT_OF_SERVICE : Status.UP;
		return Health.status(status).withDetails(details).build();
	}
}
