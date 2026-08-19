package com.example.order.controller;

import com.example.order.dto.EventDto;
import com.example.order.repository.OutboxRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Surfaces the most recent outbox events (PENDING + PUBLISHED) so the frontend
 * can render the P3 Outbox event stream. PENDING = written to the outbox in the
 * order's transaction but not yet relayed to Kafka (the relay runs only with a
 * configured broker); PUBLISHED = relayed (at-least-once delivered).
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
                .findTop20ByOrderByCreatedAtDescIdDesc()
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
