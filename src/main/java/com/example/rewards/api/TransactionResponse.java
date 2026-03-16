package com.example.rewards.api;

import com.example.rewards.ledger.EntryDirection;
import com.example.rewards.ledger.TransactionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        EntryDirection direction,
        long amount,
        String currency,
        String idempotencyKey,
        UUID relatedAccountId,
        UUID referenceTransactionId,
        String reason,
        OffsetDateTime createdAt
) {
}