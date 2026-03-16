package com.example.rewards.common;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super("RATE_LIMITED", message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
