package com.example.rewards.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReversalRequest(
        @NotNull UUID originalTransactionId,
        @Size(max = 255) String reason
) {
}