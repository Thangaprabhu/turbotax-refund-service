package com.turbotax.auth.domain.dto.response;

import com.turbotax.auth.domain.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
    @Schema(example = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJiMDNmYzA2OC1jMzZhLTQ4MjAtOTE4Zi1lMGM3NjQ5N2RlMmEiLCJlbWFpbCI6InRheHBheWVyQGV4YW1wbGUuY29tIn0.signature") String accessToken,
    @Schema(example = "Bearer") String tokenType,
    @Schema(example = "28800") long expiresInSeconds,
    @Schema(example = "INDIVIDUAL") AccountType accountType
) {
    public static AuthResponse of(String token, long expiresInSeconds, AccountType accountType) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, accountType);
    }
}
