package com.demo.oracle_dump.api;

import java.util.List;

import com.demo.oracle_dump.domain.DumpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Minimal read-only status surface plus a manual retry action for FAILED dumps. */
@RestController
@RequestMapping("/api")
public class StatusController {

	private final PipelineStatusService statusService;

	public StatusController(PipelineStatusService statusService) {
		this.statusService = statusService;
	}

	@GetMapping("/status")
	public PipelineStatusService.PipelineStatus status() {
		return statusService.status();
	}

	@GetMapping("/dumps")
	public List<PipelineStatusService.DumpView> dumps(
			@RequestParam(required = false) DumpStatus status,
			@RequestParam(defaultValue = "50") int limit) {
		return statusService.recent(status, Math.clamp(limit, 1, 500));
	}

	@PostMapping("/dumps/{id}/retry")
	public ResponseEntity<PipelineStatusService.DumpView> retry(@PathVariable long id) {
		return ResponseEntity.ok(statusService.retry(id));
	}
}
