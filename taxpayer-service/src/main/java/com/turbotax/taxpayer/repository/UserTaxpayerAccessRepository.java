package com.turbotax.taxpayer.repository;

import com.turbotax.taxpayer.domain.entity.UserTaxpayerAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTaxpayerAccessRepository extends JpaRepository<UserTaxpayerAccess, UUID> {
    Page<UserTaxpayerAccess> findByUser_IdAndActiveTrue(UUID userId, Pageable pageable);
    Optional<UserTaxpayerAccess> findByUser_IdAndTaxpayer_IdAndActiveTrue(UUID userId, UUID taxpayerId);
    boolean existsByUser_IdAndTaxpayer_IdAndActiveTrue(UUID userId, UUID taxpayerId);
}
