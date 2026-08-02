package com.turbotax.refund.domain.dto.request;

import com.turbotax.refund.domain.enums.FormType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateFilingRequest(
    @Schema(example = "F1040") @NotNull FormType formType,
    @Schema(example = "2025") @NotBlank @Pattern(regexp = "^\\d{4}$", message = "Must be a 4-digit year") String taxYear,
    @Schema(example = "FEDERAL") @NotBlank String jurisdiction,    // FEDERAL | CA | NY | etc.
    @Schema(example = "2026-02-01") @NotBlank String filingDate    // ISO 8601
) {}
