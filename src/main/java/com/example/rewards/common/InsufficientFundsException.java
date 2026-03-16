package com.example.rewards.common;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApiException {
    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", message, HttpStatus.CONFLICT);
    }
}