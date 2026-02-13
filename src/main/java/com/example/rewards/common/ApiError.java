package com.example.rewards.common;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        String code,
        String message,
        String requestId,
        OffsetDateTime timestamp,
        List<FieldValidationError> fieldErrors
) {
}