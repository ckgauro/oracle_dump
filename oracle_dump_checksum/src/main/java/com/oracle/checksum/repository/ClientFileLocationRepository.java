package com.oracle.checksum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.oracle.checksum.domain.ClientFileLocation;

public interface ClientFileLocationRepository extends JpaRepository<ClientFileLocation, Long> {

    List<ClientFileLocation> findByActiveTrueAndClient_ActiveTrue();
}
