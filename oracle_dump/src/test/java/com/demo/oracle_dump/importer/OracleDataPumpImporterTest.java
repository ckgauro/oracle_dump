package com.demo.oracle_dump.importer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.demo.oracle_dump.config.ClientDefinition;
import com.demo.oracle_dump.config.OracleImportProperties;
import com.demo.oracle_dump.config.OracleImportProperties.Importer.Mode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleDataPumpImporterTest {

	private OracleImportProperties props() {
		OracleImportProperties p = new OracleImportProperties();
		p.getOracleClient().setImpdpPath("C:/Oracle/product/19c/client_1/bin/impdp.exe");
		p.getOracleClient().setOracleHome("C:/Oracle/product/19c/client_1");
		p.getOracleClient().setTnsAdmin("C:/Oracle/network/admin");
		return p;
	}

	private ClientDefinition client() {
		ClientDefinition c = new ClientDefinition();
		c.setClientId("client-a");
		c.setSourceSchema("APP");
		c.setTargetSchema("CLIENT_A");
		c.setOracleDirectoryName("CLIENT_A_IMPORT_DIR");
		return c;
	}

	@Test
	void realImportIsStillTodo() {
		OracleDataPumpImporter importer = new OracleDataPumpImporter(props(), Mode.IMPDP);
		ImportRequest req = new ImportRequest(client(), Path.of("D:/OracleDumps/ClientA/backup.dmp"),
				"abc", Path.of("./log.txt"), Instant.now());
		assertThatThrownBy(() -> importer.importDump(req))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("pending");
	}

	@Test
	void buildsDiscreteArgumentsWithoutCmdExe() {
		OracleDataPumpImporter importer = new OracleDataPumpImporter(props(), Mode.IMPDP);
		ProcessBuilder pb = importer.buildProcessBuilder(client(), "backup.dmp",
				"system@//db-host:1521/ORCLPDB1");

		List<String> cmd = pb.command();
		assertThat(cmd.get(0)).endsWith("impdp.exe");
		assertThat(cmd).noneMatch(a -> a.equalsIgnoreCase("cmd.exe") || a.equals("/c"));
		assertThat(cmd).contains("DIRECTORY=CLIENT_A_IMPORT_DIR");
		assertThat(cmd).contains("DUMPFILE=backup.dmp");
		assertThat(cmd).contains("REMAP_SCHEMA=APP:CLIENT_A");
		// credentials are the caller's concern and must never be a single concatenated command string
		assertThat(cmd).noneMatch(a -> a.contains("impdp ") && a.contains("/"));
	}

	@Test
	void appliesOracleEnvironmentToChildOnly() {
		OracleDataPumpImporter importer = new OracleDataPumpImporter(props(), Mode.IMPDP);
		Map<String, String> childEnv = new HashMap<>();
		importer.applyOracleEnvironment(childEnv);
		assertThat(childEnv).containsEntry("ORACLE_HOME", "C:/Oracle/product/19c/client_1");
		assertThat(childEnv).containsEntry("TNS_ADMIN", "C:/Oracle/network/admin");
	}

	@Test
	void reportsCorrectMode() {
		assertThat(new OracleDataPumpImporter(props(), Mode.IMP).mode()).isEqualTo(Mode.IMP);
		assertThat(new OracleDataPumpImporter(props(), Mode.IMPDP).mode()).isEqualTo(Mode.IMPDP);
	}
}
