package com.oracle.checksum.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oracle.checksum.domain.DumpFileRecord;
import com.oracle.checksum.repository.DumpFileRecordRepository;
import com.oracle.checksum.scheduler.ChecksumScanScheduler;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/checksum")
@RequiredArgsConstructor
public class ChecksumStatusController {

    private final ChecksumScanScheduler checksumScanScheduler;
    private final DumpFileRecordRepository dumpFileRecordRepository;

    @PostMapping("/scan")
    public Map<String, String> triggerScan() {
        checksumScanScheduler.scan();
        return Map.of("status", "scan triggered");
    }

    @GetMapping("/records")
    public List<DumpFileRecord> records() {
        return dumpFileRecordRepository.findAll();
    }

    @GetMapping("/status")
    public Map<String, Long> statusSummary() {
        return dumpFileRecordRepository.findAll().stream()
                .collect(Collectors.groupingBy(record -> record.getStatus().name(), Collectors.counting()));
    }
}
