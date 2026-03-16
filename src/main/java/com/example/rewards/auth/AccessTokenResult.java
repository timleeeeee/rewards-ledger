package com.example.rewards.auth;

import java.time.OffsetDateTime;

public record AccessTokenResult(
        String token,
        OffsetDateTime expiresAt,
        long expiresInSeconds
) {
}
