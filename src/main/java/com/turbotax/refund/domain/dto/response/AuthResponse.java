package com.turbotax.refund.domain.dto.response;

import com.turbotax.refund.domain.enums.AccountType;

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
