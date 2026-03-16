package com.example.rewards.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String email,
        OffsetDateTime createdAt
) {
}
