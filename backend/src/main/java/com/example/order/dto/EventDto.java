package com.example.order.dto;

import com.example.order.model.OutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight projection of an {@code OutboxEvent} for the UI event stream.
 * Mirrors the published-to-Kafka lifecycle so the frontend can show what the
 * Outbox relay has already delivered (P3 / at-least-once semantics).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDto {

    private String id;
    private String eventType;
    private String aggregateId;
    private OutboxStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
