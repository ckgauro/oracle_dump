package com.demo.oracle_dump.io;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IoFailureTest {

	@Test
	void classifiesSharingViolationAsRetryable() {
		IoFailure f = IoFailure.classify(new IOException(
				"The process cannot access the file because it is being used by another process"));
		assertThat(f.kind()).isEqualTo(IoFailure.Kind.SHARING_VIOLATION);
		assertThat(f.retryable()).isTrue();
	}

	@Test
	void classifiesNetworkLossAsRetryable() {
		IoFailure f = IoFailure.classify(
				new IOException("The specified network name is no longer available"));
		assertThat(f.kind()).isEqualTo(IoFailure.Kind.NETWORK_UNAVAILABLE);
		assertThat(f.retryable()).isTrue();
	}

	@Test
	void classifiesAccessDeniedTypeAsRetryable() {
		IoFailure f = IoFailure.classify(new AccessDeniedException("D:/dump/backup.dmp"));
		assertThat(f.kind()).isEqualTo(IoFailure.Kind.ACCESS_DENIED);
		assertThat(f.retryable()).isTrue();
	}

	@Test
	void classifiesMissingFileAsNonRetryable() {
		IoFailure f = IoFailure.classify(new NoSuchFileException("gone.dmp"));
		assertThat(f.kind()).isEqualTo(IoFailure.Kind.NOT_FOUND);
		assertThat(f.retryable()).isFalse();
	}

	@Test
	void walksCauseChainForMessage() {
		IOException root = new IOException("The network path was not found");
		RuntimeException wrapper = new RuntimeException("scan failed", root);
		assertThat(IoFailure.classify(wrapper).kind()).isEqualTo(IoFailure.Kind.NETWORK_UNAVAILABLE);
	}

	@Test
	void unknownDefaultsToRetryable() {
		IoFailure f = IoFailure.classify(new IllegalStateException("something odd"));
		assertThat(f.kind()).isEqualTo(IoFailure.Kind.UNKNOWN);
		assertThat(f.retryable()).isTrue();
	}
}
