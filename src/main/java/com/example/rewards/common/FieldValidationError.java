package com.example.rewards.common;

public record FieldValidationError(
        String field,
        String message
) {
}