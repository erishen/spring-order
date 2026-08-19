package com.example.order.controller;

import com.example.order.service.OrderProjection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Surfaces the consumer's read-model projection so the runtime-status page can
 * show that an event, once published to Kafka, was consumed and produced new
 * state. Active only with a configured broker (the `kafka` profile), matching
 * the consumer it reflects.
 */
@RestController
@RequestMapping("/api/consumer")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class ConsumerProjectionController {

    private final OrderProjection projection;

    public ConsumerProjectionController(OrderProjection projection) {
        this.projection = projection;
    }

    @GetMapping("/projection")
    public ResponseEntity<ProjectionView> projection() {
        List<ProjectionView.ConsumedOrderView> recent = projection.getRecent().stream()
                .map(o -> new ProjectionView.ConsumedOrderView(
                        o.orderId, o.userId, o.amount, o.discount, o.finalAmount, o.status, o.consumedAt))
                .toList();
        List<String> consumedOrderIds = projection.getRecent().stream()
                .map(o -> o.orderId)
                .toList();
        ProjectionView view = new ProjectionView(
                projection.getTotalConsumed(),
                projection.getLastConsumedAt() == null ? null : projection.getLastConsumedAt().toString(),
                recent,
                consumedOrderIds);
        return ResponseEntity.ok(view);
    }

    public static class ProjectionView {
        public final long totalConsumed;
        public final String lastConsumedAt;
        public final List<ConsumedOrderView> recentOrders;
        public final List<String> consumedOrderIds;

        public ProjectionView(long totalConsumed, String lastConsumedAt,
                              List<ConsumedOrderView> recentOrders, List<String> consumedOrderIds) {
            this.totalConsumed = totalConsumed;
            this.lastConsumedAt = lastConsumedAt;
            this.recentOrders = recentOrders;
            this.consumedOrderIds = consumedOrderIds;
        }

        public static class ConsumedOrderView {
            public final String orderId;
            public final String userId;
            public final String amount;
            public final String discount;
            public final String finalAmount;
            public final String status;
            public final String consumedAt;

            public ConsumedOrderView(String orderId, String userId, String amount, String discount,
                                     String finalAmount, String status, String consumedAt) {
                this.orderId = orderId;
                this.userId = userId;
                this.amount = amount;
                this.discount = discount;
                this.finalAmount = finalAmount;
                this.status = status;
                this.consumedAt = consumedAt;
            }
        }
    }
}
