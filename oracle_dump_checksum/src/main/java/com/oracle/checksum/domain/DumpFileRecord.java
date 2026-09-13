package com.oracle.checksum.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dump_file_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "file_path"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DumpFileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "last_modified")
    private Instant lastModified;

    @Column(name = "checksum_algorithm")
    private String checksumAlgorithm;

    @Column(name = "checksum_value")
    private String checksumValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DumpFileStatus status;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
