package com.demo.oracle_dump.staging;

import java.nio.file.Path;

import com.demo.oracle_dump.config.ClientDefinition;

/**
 * Bridges the two filesystem contexts the spec is emphatic about keeping separate (CLAUDE.md §15,
 * §16, "Important Oracle Rule"): the <em>source dump directory</em> that Spring Boot can see, and the
 * <em>Oracle Data Pump DIRECTORY</em> that the database server can see. Data Pump reads through a
 * {@code DIRECTORY} object, not an arbitrary Windows/UNC path, so a staging copy is often required.
 *
 * <p><strong>Not implemented.</strong> Per §16 this must wait until the real Oracle deployment
 * architecture is confirmed. The interface exists so the pipeline has an obvious seam to add it at.
 * Intended responsibilities: copy to an Oracle-accessible directory, verify staged size/checksum,
 * skip re-copying files already staged, and clean up per a retention policy — all streamed, never
 * buffering a 20 GB file in memory (§17).
 */
public interface DumpStagingService {

	/**
	 * Make {@code sourceDump} available to Oracle Data Pump for {@code client} and return the path (or
	 * bare filename) to reference via the client's {@code DIRECTORY} object.
	 */
	StagedDump stage(ClientDefinition client, Path sourceDump);

	/** Remove a previously staged file once its import is complete and retention allows. */
	void cleanup(ClientDefinition client, StagedDump staged);

	/**
	 * @param stagedPath   absolute path of the staged copy on the Oracle-accessible volume
	 * @param dumpFileName bare name to put in {@code DUMPFILE=}
	 * @param reused       true if an existing staged copy was reused instead of copying again
	 */
	record StagedDump(Path stagedPath, String dumpFileName, boolean reused) {
	}
}
