package com.turbotax.refund.domain.dto.request;

import com.turbotax.refund.domain.enums.FormType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateFilingRequest(
    @NotNull FormType formType,
    @NotBlank @Pattern(regexp = "^\\d{4}$", message = "Must be a 4-digit year") String taxYear,
    @NotBlank String jurisdiction,    // FEDERAL | CA | NY | etc.
    @NotBlank String filingDate       // ISO 8601
) {}
