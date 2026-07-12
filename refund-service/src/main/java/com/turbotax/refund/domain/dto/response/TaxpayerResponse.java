package com.turbotax.refund.domain.dto.response;

import com.turbotax.refund.domain.enums.TaxpayerType;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaxpayerResponse(
    UUID id,
    TaxpayerType taxpayerType,
    String displayName,
    String entityType,
    String stateOfReg,
    LocalDateTime createdAt
) {}
