package com.example.order.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Example downstream consumer (the "other service" in an event-driven design).
 * Active only with the `kafka` profile. Real consumers would update
 * read-models, send notifications, trigger fulfilment, etc. — and must be
 * idempotent, keyed by the event's aggregate id (OrderCreated -> orderId),
 * which pairs with the P2 Idempotency-Key on the write path.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class OrderEventLogger {

    private static final Logger log = LoggerFactory.getLogger(OrderEventLogger.class);

    @KafkaListener(
            topics = "${order.kafka.topic:order-events}",
            groupId = "${spring.kafka.consumer.group-id:order-platform}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        log.info("Consumed {} event -> key={}, payload={}", record.topic(), record.key(), record.value());
    }
}
