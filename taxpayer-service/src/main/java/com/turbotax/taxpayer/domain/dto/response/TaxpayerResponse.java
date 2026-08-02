package com.turbotax.taxpayer.domain.dto.response;

import com.turbotax.taxpayer.domain.enums.TaxpayerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaxpayerResponse(
    @Schema(example = "b1a45447-b096-45f4-bb28-02a9d33a7cc4") UUID id,
    @Schema(example = "INDIVIDUAL") TaxpayerType taxpayerType,
    @Schema(example = "Jane Doe") String displayName,
    @Schema(example = "LLC") String entityType,
    @Schema(example = "CA") String stateOfReg,
    @Schema(example = "2026-02-01T10:15:30") LocalDateTime createdAt
) {}
