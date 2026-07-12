package com.turbotax.auth.domain.dto.request;

import com.turbotax.auth.domain.enums.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    AccountType accountType
) {
    public RegisterRequest {
        if (accountType == null) accountType = AccountType.INDIVIDUAL;
    }
}
