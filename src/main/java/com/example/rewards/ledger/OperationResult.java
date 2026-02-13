package com.example.rewards.ledger;

import java.util.UUID;

public record OperationResult(
        UUID primaryTransactionId,
        UUID secondaryTransactionId
) {
}