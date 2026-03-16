package com.example.rewards.api;

import com.example.rewards.account.AccountStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        AccountStatus status,
        long balance,
        OffsetDateTime createdAt
) {
}