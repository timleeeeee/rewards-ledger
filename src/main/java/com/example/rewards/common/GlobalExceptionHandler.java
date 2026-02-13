package com.example.rewards.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(
                new ApiError(
                        ex.getCode(),
                        ex.getMessage(),
                        requestId(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity.badRequest().body(
                new ApiError(
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        requestId(),
                        OffsetDateTime.now(),
                        fieldErrors
                )
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError(
                        "MISSING_HEADER",
                        ex.getHeaderName() + " header is required",
                        requestId(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError(
                        "INVALID_JSON",
                        "Malformed JSON request body",
                        requestId(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError(
                        "BAD_REQUEST",
                        "Invalid parameter format",
                        requestId(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiError(
                        "INTERNAL_ERROR",
                        "Unexpected error",
                        requestId(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    private FieldValidationError toFieldError(FieldError fieldError) {
        return new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "unknown" : requestId;
    }
}
