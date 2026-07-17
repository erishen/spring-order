package com.example.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka wiring — only active when a broker is configured (the `kafka` profile).
 * Without it, this bean (and OutboxRelay / OrderEventLogger) is never created,
 * so the app runs with zero Kafka dependency; outbox rows simply stay PENDING
 * until a broker becomes available.
 *
 * KafkaTemplate + producer/consumer factories are auto-configured by
 * spring-kafka once `spring.kafka.bootstrap-servers` is set, so we only declare
 * the topic here.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    @Bean
    public NewTopic orderEventsTopic(@Value("${order.kafka.topic:order-events}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
