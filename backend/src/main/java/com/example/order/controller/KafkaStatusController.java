package com.example.order.controller;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Exposes the Kafka wiring/connectivity state so the runtime-status page can
 * show why outbox events are still PENDING (no broker) vs PUBLISHED (relayed).
 */
@RestController
@RequestMapping("/api/kafka")
public class KafkaStatusController {

    @Value("${spring.kafka.bootstrap-servers:}")
    private String bootstrapServers;

    // 容器内 listener（docker-compose 里的 order-svc 副本走 kafka:9092）。
    // 宿主机后端（make dev）走 spring.kafka.bootstrap-servers（localhost:9094）。
    @Value("${kafka.internal.bootstrap-servers:kafka:9092}")
    private String internalBootstrapServers;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        boolean configured = bootstrapServers != null && !bootstrapServers.isBlank();
        boolean connected = configured && probe();
        return ResponseEntity.ok(Map.of(
                "configured", configured,
                "connected", connected,
                "bootstrapServers", bootstrapServers == null ? "" : bootstrapServers,
                "internalBootstrapServers", internalBootstrapServers));
    }

    private boolean probe() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        try (AdminClient client = AdminClient.create(props)) {
            client.describeCluster().nodes().get(2, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
