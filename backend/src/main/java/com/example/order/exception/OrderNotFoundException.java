package com.example.order.exception;

/**
 * Thrown when a requested order does not exist. Mapped to HTTP 404 by the
 * global handler (distinct from 400 validation errors).
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
