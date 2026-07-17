package com.example.order.exception;

/**
 * Thrown when a request arrives with an Idempotency-Key that is currently
 * being processed by another in-flight request (status = IN_PROGRESS).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("A request with the same Idempotency-Key '" + key + "' is already in progress");
    }
}
