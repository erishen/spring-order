package com.example.order.model;

/**
 * Lifecycle of an idempotency key reservation.
 * IN_PROGRESS -> a request currently owns the key and is processing.
 * COMPLETED   -> processing finished; the stored response_body can be replayed.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
