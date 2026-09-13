package com.oracle.checksum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.oracle.checksum.domain.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByActiveTrue();
}
