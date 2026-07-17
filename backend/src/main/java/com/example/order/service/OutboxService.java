package com.example.order.service;

import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes and marks outbox events.
 *
 * - {@link #saveEvent(String, String, String, Object)} runs in the CALLER's
 *   transaction (REQUIRED), so the outbox row is committed together with the
 *   business row it describes — the core guarantee of the Outbox pattern.
 * - {@link #markPublished(String)} uses REQUIRES_NEW so the "delivered" marker
 *   commits independently of (and after) the Kafka send.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutboxEvent saveEvent(String aggregateType, String aggregateId, String eventType, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
        return saveEvent(aggregateType, aggregateId, eventType, payload);
    }

    @Transactional
    public OutboxEvent saveEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEvent row = new OutboxEvent(aggregateType, aggregateId, eventType, payload);
        row.setId(UUID.randomUUID().toString());
        return repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String id) {
        repository.findById(id).ifPresent(row -> {
            row.setStatus(OutboxStatus.PUBLISHED);
            row.setPublishedAt(java.time.LocalDateTime.now());
            repository.save(row);
        });
    }
}
