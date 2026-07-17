package com.example.order.service;

import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains the transactional outbox and publishes each PENDING row to Kafka.
 *
 * Runs only when a broker is configured (the `kafka` profile). Delivery is
 * at-least-once: a row may be sent more than once if the relay dies between
 * the successful send and {@link OutboxService#markPublished(String)}, so
 * downstream consumers MUST be idempotent (see P2 Idempotency-Key).
 *
 * A failed send leaves the row PENDING so the next poll retries it — events
 * are never lost even if Kafka is temporarily down.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@EnableScheduling
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxRelay(OutboxRepository repository,
                       OutboxService outboxService,
                       KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${order.kafka.topic:order-events}") String topic) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${order.kafka.relay.fixed-delay:1000}")
    public void relay() {
        publishPending();
    }

    /** Visible for direct invocation from tests. */
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.findByStatusOrderByIdAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            try {
                SendResult<String, String> result = kafkaTemplate
                        .send(new ProducerRecord<>(topic, event.getAggregateId(), event.getPayload()))
                        .get(5, TimeUnit.SECONDS);
                outboxService.markPublished(event.getId());
                log.debug("Published outbox event {} to {}", event.getId(), result.getRecordMetadata().topic());
            } catch (Exception ex) {
                // Leave PENDING for the next poll; at-least-once, never drop.
                log.warn("Outbox publish failed for {} (will retry): {}", event.getId(), ex.getMessage());
            }
        }
    }
}
