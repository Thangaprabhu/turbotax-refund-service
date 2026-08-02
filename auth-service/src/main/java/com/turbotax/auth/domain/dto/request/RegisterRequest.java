package com.turbotax.auth.domain.dto.request;

import com.turbotax.auth.domain.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Schema(example = "taxpayer@example.com") @NotBlank @Email String email,
    @Schema(example = "SecurePass123!") @NotBlank @Size(min = 8, max = 100) String password,
    @Schema(example = "INDIVIDUAL") AccountType accountType
) {
    public RegisterRequest {
        if (accountType == null) accountType = AccountType.INDIVIDUAL;
    }
}
