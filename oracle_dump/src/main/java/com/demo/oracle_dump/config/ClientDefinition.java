package com.demo.oracle_dump.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * One tenant whose dump files are scanned and imported.
 *
 * <p>The spec (CLAUDE.md §27) is deliberate that the on-disk directory name, the source schema and
 * the target schema are independent values, so they are modelled separately here rather than being
 * derived from {@code clientId}.
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class ClientDefinition {

	@ToString.Include
	@NotBlank
	private String clientId;

	@ToString.Include
	private boolean enabled = true;

	/**
	 * Where dump files land. Local Windows path ({@code D:/OracleDumps/ClientB}) or UNC share
	 * ({@code //nas01/oracle-dumps/ClientB}). Never a mapped drive for a Windows Service.
	 */
	@ToString.Include
	@NotBlank
	private String dumpDirectory;

	/** Schema inside the dump file (the {@code REMAP_SCHEMA} source). Optional for full-database dumps. */
	private String sourceSchema;

	/** Schema the data should land in on the target database. */
	@ToString.Include
	@NotBlank
	private String targetSchema;

	/** Oracle {@code DIRECTORY} object name Data Pump reads from. Distinct from {@link #dumpDirectory}. */
	private String oracleDirectoryName;

	/** Logical name of the target DB connection (resolved elsewhere once real imports exist). */
	private String databaseConnectionName;

	/** Concurrent imports allowed for this one client. */
	@ToString.Include
	@Min(1)
	private int maxParallelImports = 1;
}
