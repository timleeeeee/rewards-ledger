package com.example.rewards.api;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> items,
        String nextCursor
) {
}