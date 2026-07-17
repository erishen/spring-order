package com.example.order.service;

import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox write/mark logic — runs with the DEFAULT profile (no Kafka broker),
 * so it verifies the DB half of the pattern without needing any infrastructure.
 */
@SpringBootTest
class OutboxServiceTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void saveEvent_writesPendingRow() {
        OutboxEvent row = outboxService.saveEvent("Order", "o-1", "OrderCreated",
                Map.of("orderId", "o-1", "amount", 100));

        OutboxEvent stored = outboxRepository.findById(row.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, stored.getStatus());
        assertEquals("OrderCreated", stored.getEventType());
        assertEquals("Order", stored.getAggregateType());
        assertTrue(stored.getPayload().contains("o-1"));
        assertNotNull(stored.getCreatedAt());
    }

    @Test
    void markPublished_flipsStatusAndTimestamp() {
        OutboxEvent row = outboxService.saveEvent("Order", "o-2", "OrderCreated", "{\"x\":1}");

        outboxService.markPublished(row.getId());

        OutboxEvent stored = outboxRepository.findById(row.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, stored.getStatus());
        assertNotNull(stored.getPublishedAt());
    }
}
