package com.example.order.controller;

import com.example.order.dto.EventDto;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Surfaces the recently-published outbox events so the frontend can render the
 * Kafka event stream produced by the P3 Outbox relay. Only PUBLISHED rows are
 * returned — the relay guarantees these have been (at-least-once) delivered.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final OutboxRepository outboxRepository;

    public EventController(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping
    public ResponseEntity<List<EventDto>> recentEvents() {
        List<EventDto> events = outboxRepository
                .findTop20ByStatusOrderByIdDesc(OutboxStatus.PUBLISHED)
                .stream()
                .map(e -> new EventDto(
                        e.getId(),
                        e.getEventType(),
                        e.getAggregateId(),
                        e.getStatus(),
                        e.getCreatedAt(),
                        e.getPublishedAt()))
                .toList();
        return ResponseEntity.ok(events);
    }
}
