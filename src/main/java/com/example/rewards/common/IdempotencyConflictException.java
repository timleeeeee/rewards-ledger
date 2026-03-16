package com.example.rewards.common;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends ApiException {
    public IdempotencyConflictException(String message) {
        super("IDEMPOTENCY_CONFLICT", message, HttpStatus.CONFLICT);
    }
}