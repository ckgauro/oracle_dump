package com.oracle.checksum.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.checksum.domain.ClientFileLocation;
import com.oracle.checksum.repository.ClientFileLocationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads client/path configuration from H2 (client, client_file_location).
 * Client-to-path mapping is data, not YAML, so adding a client or changing
 * a path is a row change that takes effect on the next scan - no redeploy.
 */
@Service
@RequiredArgsConstructor
public class ClientConfigService {

    private final ClientFileLocationRepository clientFileLocationRepository;

    @Transactional(readOnly = true)
    public List<ClientFileLocation> activeLocations() {
        return clientFileLocationRepository.findByActiveTrueAndClient_ActiveTrue();
    }
}
