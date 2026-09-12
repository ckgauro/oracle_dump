package com.demo.oracle_dump.scanner;

import com.demo.oracle_dump.config.ClientRegistry;
import com.demo.oracle_dump.config.ClientRegistry.ResolvedClient;
import com.demo.oracle_dump.io.IoFailure;
import com.demo.oracle_dump.lifecycle.PipelineState;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates a scan across every enabled client. Each client is scanned in its own transaction by
 * {@link ClientDirectoryScanner}; failures are classified, logged and isolated so one bad share never
 * stops the others (CLAUDE.md §29).
 */
@Slf4j
@Component
public class DirectoryScanner {

	private final ClientRegistry clients;
	private final ClientDirectoryScanner clientScanner;
	private final PipelineState pipelineState;

	public DirectoryScanner(ClientRegistry clients, ClientDirectoryScanner clientScanner,
			PipelineState pipelineState) {
		this.clients = clients;
		this.clientScanner = clientScanner;
		this.pipelineState = pipelineState;
	}

	public ScanSummary scanAll() {
		if (!pipelineState.isAcceptingWork()) {
			log.debug("Pipeline draining; skipping scan");
			return ScanSummary.empty();
		}
		ScanSummary total = ScanSummary.empty();
		for (ResolvedClient client : clients.enabled()) {
			try {
				total = total.plus(clientScanner.scan(client));
			}
			catch (RuntimeException ex) {
				IoFailure failure = IoFailure.classify(ex);
				log.warn("Scan failed for client '{}' dir={} kind={} : {}", client.id(),
						client.dumpDirectory(), failure.kind(), failure.message());
			}
		}
		return total;
	}
}
