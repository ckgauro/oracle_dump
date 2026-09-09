package com.demo.oracle_dump.io;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsPathsTest {

	@Test
	void rejectsBlank() {
		assertThatThrownBy(() -> WindowsPaths.toNormalizedPath("  "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void localPathIsAbsoluteAndNormalized() {
		Path p = WindowsPaths.toNormalizedPath("./a/b/../c");
		assertThat(p.isAbsolute()).isTrue();
		assertThat(p.toString().replace('\\', '/')).endsWith("/a/c");
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void uncPathIsPreservedVerbatimOnNonWindows() {
		Path p = WindowsPaths.toNormalizedPath("//nas01/oracle-dumps/client-a");
		assertThat(p.toString()).isEqualTo("\\\\nas01\\oracle-dumps\\client-a");
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void uncPathKeepsUncRootOnWindows() {
		Path p = WindowsPaths.toNormalizedPath("//nas01/oracle-dumps/client-a");
		assertThat(p.toString()).startsWith("\\\\nas01\\");
	}

	@Test
	void backslashUncIsAlsoAccepted() {
		Path p = WindowsPaths.toNormalizedPath("\\\\fileserver02\\backup\\ClientB");
		assertThat(p.toString()).isEqualTo("\\\\fileserver02\\backup\\ClientB");
	}

	@Test
	void resolveChildUsesNioResolution() {
		Path base = WindowsPaths.toNormalizedPath("./base");
		assertThat(WindowsPaths.resolveChild(base, "file.dmp").getFileName().toString())
				.isEqualTo("file.dmp");
	}
}
