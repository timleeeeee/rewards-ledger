package com.example.rewards.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AmountRequest(
        @NotNull @Min(1) @Max(1_000_000) Long amount,
        @Size(max = 255) String reason,
        @Size(max = 16) String currency
) {
}
