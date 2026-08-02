package com.turbotax.taxpayer.domain.dto.request;

import com.turbotax.taxpayer.domain.enums.TaxpayerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaxpayerRequest(
    @Schema(example = "INDIVIDUAL") @NotNull TaxpayerType taxpayerType,
    @Schema(example = "123-45-6789") @NotBlank @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{4}$|^\\d{2}-\\d{7}$",
        message = "Must be a valid SSN (XXX-XX-XXXX) or EIN (XX-XXXXXXX)")
    String taxId,
    @Schema(example = "Jane Doe") @NotBlank @Size(max = 200) String displayName,
    @Schema(example = "LLC") String entityType,   // null for INDIVIDUAL
    @Schema(example = "CA") @Size(min = 2, max = 2) String stateOfReg
) {}
