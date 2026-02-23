package com.example.rewards.api;

public record AuthResponse(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        AuthUserResponse user
) {
}
