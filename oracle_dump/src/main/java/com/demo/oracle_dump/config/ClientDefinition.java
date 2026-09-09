package com.demo.oracle_dump.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * One tenant whose dump files are scanned and imported.
 *
 * <p>The spec (CLAUDE.md §27) is deliberate that the on-disk directory name, the source schema and
 * the target schema are independent values, so they are modelled separately here rather than being
 * derived from {@code clientId}.
 */
public class ClientDefinition {

	@NotBlank
	private String clientId;

	private boolean enabled = true;

	/**
	 * Where dump files land. Local Windows path ({@code D:/OracleDumps/ClientB}) or UNC share
	 * ({@code //nas01/oracle-dumps/ClientB}). Never a mapped drive for a Windows Service.
	 */
	@NotBlank
	private String dumpDirectory;

	/** Schema inside the dump file (the {@code REMAP_SCHEMA} source). Optional for full-database dumps. */
	private String sourceSchema;

	/** Schema the data should land in on the target database. */
	@NotBlank
	private String targetSchema;

	/** Oracle {@code DIRECTORY} object name Data Pump reads from. Distinct from {@link #dumpDirectory}. */
	private String oracleDirectoryName;

	/** Logical name of the target DB connection (resolved elsewhere once real imports exist). */
	private String databaseConnectionName;

	/** Concurrent imports allowed for this one client. */
	@Min(1)
	private int maxParallelImports = 1;

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getDumpDirectory() {
		return dumpDirectory;
	}

	public void setDumpDirectory(String dumpDirectory) {
		this.dumpDirectory = dumpDirectory;
	}

	public String getSourceSchema() {
		return sourceSchema;
	}

	public void setSourceSchema(String sourceSchema) {
		this.sourceSchema = sourceSchema;
	}

	public String getTargetSchema() {
		return targetSchema;
	}

	public void setTargetSchema(String targetSchema) {
		this.targetSchema = targetSchema;
	}

	public String getOracleDirectoryName() {
		return oracleDirectoryName;
	}

	public void setOracleDirectoryName(String oracleDirectoryName) {
		this.oracleDirectoryName = oracleDirectoryName;
	}

	public String getDatabaseConnectionName() {
		return databaseConnectionName;
	}

	public void setDatabaseConnectionName(String databaseConnectionName) {
		this.databaseConnectionName = databaseConnectionName;
	}

	public int getMaxParallelImports() {
		return maxParallelImports;
	}

	public void setMaxParallelImports(int maxParallelImports) {
		this.maxParallelImports = maxParallelImports;
	}

	@Override
	public String toString() {
		return "ClientDefinition{clientId='" + clientId + "', enabled=" + enabled
				+ ", dumpDirectory='" + dumpDirectory + "', targetSchema='" + targetSchema
				+ "', maxParallelImports=" + maxParallelImports + "}";
	}
}
