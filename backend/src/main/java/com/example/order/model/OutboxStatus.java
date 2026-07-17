package com.example.order.model;

/**
 * Lifecycle of an outbox row.
 * PENDING   -> not yet delivered to Kafka
 * PUBLISHED -> delivered (at-least-once: a PENDING row may be sent more than
 *              once if the relay crashes between send and markPublished, so
 *              downstream consumers MUST be idempotent — see P2 Idempotency-Key)
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
