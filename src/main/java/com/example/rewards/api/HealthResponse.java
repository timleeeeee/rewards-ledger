package com.example.rewards.api;

import java.util.Map;

public record HealthResponse(
        String status,
        String version,
        Map<String, String> checks
) {
}