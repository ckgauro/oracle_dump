package com.oracle.checksum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "client_file_location")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientFileLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "base_path", nullable = false)
    private String basePath;

    /** Glob pattern, e.g. *.dmp. Defaults to *.dmp when null. */
    @Column(name = "file_pattern")
    private String filePattern;

    @Column(nullable = false)
    private boolean active;

    public String resolveFilePattern() {
        return filePattern != null ? filePattern : "*.dmp";
    }
}
