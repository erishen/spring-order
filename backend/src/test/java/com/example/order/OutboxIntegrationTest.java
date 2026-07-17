package com.example.order;

import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.repository.OutboxRepository;
import com.example.order.service.OutboxService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Outbox proof: a PENDING row is drained by OutboxRelay (scheduled
 * poll) and the OrderCreated event actually lands on a real (in-JVM) Kafka
 * broker, then the row is flipped to PUBLISHED — and the event is consumable
 * from the topic.
 *
 * Uses spring-kafka-test's EmbeddedKafkaBroker — no Docker required. The
 * bootstrap servers are injected into `spring.kafka.bootstrap-servers`, which
 * is exactly the property that activates KafkaConfig / OutboxRelay / the
 * example consumer in production (the `kafka` profile).
 *
 * The consumer here is a plain KafkaConsumer polling the broker directly
 * (rather than a @KafkaListener), which makes the test immune to listener
 * container auto-startup ordering under EmbeddedKafka.
 */
@SpringBootTest(properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = "order-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void publishesPendingOrderEventToKafka() throws Exception {
        outboxService.saveEvent("Order", "o-kafka", "OrderCreated",
                Map.of("orderId", "o-kafka", "amount", 100));

        // 1) The scheduled OutboxRelay must drain the PENDING row.
        boolean published = waitFor(() -> outboxRepository
                .findByAggregateIdAndEventType("o-kafka", "OrderCreated")
                .map(e -> e.getStatus() == OutboxStatus.PUBLISHED)
                .orElse(false), 20);
        assertTrue(published, "OutboxRelay should drain the PENDING row and mark it PUBLISHED");

        // 2) The event must be consumable from the topic (outbox -> relay -> broker).
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verify-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        boolean received;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("order-events"));
            received = false;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline && !received) {
                for (var record : consumer.poll(Duration.ofSeconds(2))) {
                    if (record.value().contains("o-kafka")) {
                        received = true;
                        break;
                    }
                }
            }
        }
        assertTrue(received, "OrderCreated event should be consumable from Kafka (outbox -> relay -> broker)");
    }

    private boolean waitFor(BooleanSupplier condition, long seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }
}
