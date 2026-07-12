package com.turbotax.refund.domain.dto.request;

import com.turbotax.refund.domain.enums.TaxpayerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaxpayerRequest(
    @NotNull TaxpayerType taxpayerType,
    @NotBlank @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{4}$|^\\d{2}-\\d{7}$",
        message = "Must be a valid SSN (XXX-XX-XXXX) or EIN (XX-XXXXXXX)")
    String taxId,
    @NotBlank @Size(max = 200) String displayName,
    String entityType,   // null for INDIVIDUAL
    @Size(min = 2, max = 2) String stateOfReg
) {}
