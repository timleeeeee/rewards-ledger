package com.example.rewards.api;

import com.example.rewards.account.AccountStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAccountResponse(
        UUID id,
        AccountStatus status,
        OffsetDateTime createdAt
) {
}