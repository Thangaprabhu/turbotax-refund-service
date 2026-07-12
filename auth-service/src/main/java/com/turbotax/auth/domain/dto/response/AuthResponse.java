package com.turbotax.auth.domain.dto.response;

import com.turbotax.auth.domain.enums.AccountType;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    AccountType accountType
) {
    public static AuthResponse of(String token, long expiresInSeconds, AccountType accountType) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, accountType);
    }
}
