package com.example.order.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Example downstream consumer (the "other service" in an event-driven design).
 * Active only with the `kafka` profile.
 *
 * <p>Beyond logging, it now maintains a read-model projection
 * ({@link OrderProjection}) — exactly what a real consumer would do (update a
 * read side, send a notification, trigger fulfilment, ...). Consumption is
 * idempotent, keyed by the event's orderId, pairing with the P2 Idempotency-Key
 * on the write path. The relay delivers at-least-once, so the same event may
 * arrive more than once and must be de-duplicated here.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class OrderEventLogger {

    private static final Logger log = LoggerFactory.getLogger(OrderEventLogger.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderProjection projection;

    public OrderEventLogger(OrderProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(
            topics = "${order.kafka.topic:order-events}",
            groupId = "${spring.kafka.consumer.group-id:spring-order}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        try {
            OrderCreatedPayload payload = objectMapper.readValue(record.value(), OrderCreatedPayload.class);
            if (payload.orderId == null) {
                log.warn("Skipping order-event with null orderId: {}", record.value());
                return;
            }
            boolean first = projection.consume(
                    payload.orderId,
                    payload.userId,
                    toBigDecimal(payload.amount),
                    toBigDecimal(payload.discount),
                    toBigDecimal(payload.finalAmount),
                    payload.status,
                    LocalDateTime.now());
            if (first) {
                log.info("Consumed OrderCreated -> orderId={}, userId={}, finalAmount={} (projection updated)",
                        payload.orderId, payload.userId, payload.finalAmount);
            } else {
                log.debug("Duplicate OrderCreated (idempotent skip) -> orderId={}", payload.orderId);
            }
        } catch (Exception ex) {
            log.error("Failed to process order-event {}: {}", record.value(), ex.getMessage());
        }
    }

    private BigDecimal toBigDecimal(String v) {
        return v == null || v.isBlank() ? null : new BigDecimal(v);
    }

    /** JSON payload shape written by OrderService into the transactional outbox. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OrderCreatedPayload {
        public String orderId;
        public String userId;
        public String amount;
        public String discount;
        public String finalAmount;
        public String status;
        public String createdAt;
    }
}
