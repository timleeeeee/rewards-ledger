package com.example.rewards.auth;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email
) {
}
