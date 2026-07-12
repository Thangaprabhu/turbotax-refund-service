package com.turbotax.refund.repository;

import com.turbotax.refund.domain.entity.Taxpayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxpayerRepository extends JpaRepository<Taxpayer, UUID> {
    Optional<Taxpayer> findByTaxIdHash(String taxIdHash);
    boolean existsByTaxIdHash(String taxIdHash);
}
