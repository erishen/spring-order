package com.example.order.controller;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderWithPromotion;
import com.example.order.exception.IdempotencyConflictException;
import com.example.order.service.IdempotencyService;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<OrderWithPromotion> createOrder(
            @RequestHeader(value = IdempotencyService.IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyService.Outcome outcome = idempotencyService.checkAndReserve(idempotencyKey);
            if (outcome == IdempotencyService.Outcome.HIT) {
                // A previous attempt already completed -> replay the stored response.
                return ResponseEntity.ok(idempotencyService.getCached(idempotencyKey));
            }
            if (outcome == IdempotencyService.Outcome.IN_PROGRESS) {
                // Another in-flight request owns this key -> client must not double-submit.
                throw new IdempotencyConflictException(idempotencyKey);
            }
            // PROCEED: this request owns the key. On any failure, release it so the
            // client can safely retry with the same key (no order was persisted).
            try {
                OrderWithPromotion response = orderService.createOrder(request);
                idempotencyService.complete(idempotencyKey, response);
                return ResponseEntity
                        .created(URI.create("/api/orders/" + response.getOrder().getId()))
                        .body(response);
            } catch (Exception e) {
                idempotencyService.fail(idempotencyKey);
                throw e;
            }
        }

        // No Idempotency-Key supplied -> plain create (no replay guarantee).
        OrderWithPromotion response = orderService.createOrder(request);
        return ResponseEntity
                .created(URI.create("/api/orders/" + response.getOrder().getId()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {
        OrderResponse response = orderService.getOrder(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> responses = orderService.getAllOrders();
        return ResponseEntity.ok(responses);
    }

    /** 清空所有演示数据：订单 + Outbox 事件 + 幂等键 + 消费者投影。 */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAll() {
        long removed = orderService.clearAll();
        return ResponseEntity.ok(Map.of(
                "removed", removed,
                "message", "已清空订单、Outbox 事件、幂等键与消费者投影"));
    }
}
