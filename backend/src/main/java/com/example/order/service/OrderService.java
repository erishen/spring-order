package com.example.order.service;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderWithPromotion;
import com.example.order.dto.PromotionResult;
import com.example.order.dto.PromotionView;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final int MAX_RETRIES = 5;

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PromotionService promotionService;
    private final OutboxService outboxService;

    @Autowired(required = false)
    private RedisLockService lockService;

    public OrderService(OrderRepository orderRepository,
                        InventoryService inventoryService,
                        PromotionService promotionService,
                        OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.promotionService = promotionService;
        this.outboxService = outboxService;
    }

    /**
     * Creates an order backed by JPA. Stock is reserved through InventoryService
     * (optimistic-lock guarded); when Redis is available the deduction is also
     * serialized by a distributed lock (per stockId). On success the OrderCreated
     * event is written to the transactional outbox in the SAME DB transaction as
     * the order, then relayed to Kafka by OutboxRelay (at-least-once delivery,
     * downstream consumers stay idempotent via the P2 Idempotency-Key).
     */
    @Transactional
    @RateLimiter(name = "orderCreate")
    @CircuitBreaker(name = "orderCreate")
    @Retry(name = "orderCreate")
    @CacheEvict(cacheNames = "orders-all", allEntries = true)
    public OrderWithPromotion createOrder(OrderCreateRequest request) {
        // "new user" is derived from real order history in the database.
        boolean isNewUser = !orderRepository.existsByUserId(request.getUserId());

        String stockId = request.getStockId() != null ? request.getStockId() : "DEFAULT";
        int requiredStock = request.getRequiredStock() != null ? request.getRequiredStock() : 1;

        // Reserve stock with optimistic-lock retry (Rule 4 -> InsufficientStockException
        // on insufficient stock; OptimisticLockingFailureException -> retry, no oversell).
        // If a Redis distributed lock is configured, the deduction is serialized across
        // instances; otherwise it falls back to the DB optimistic lock alone.
        int available = reserveWithRetry(stockId, requiredStock);

        PromotionResult promotion = promotionService.applyPromotion(
                request.getAmount(), isNewUser, requiredStock, available);

        Order order = new Order(request.getUserId(), request.getAmount());
        order.setId(UUID.randomUUID().toString());
        order.setDiscount(promotion.getDiscount());
        order.setFinalAmount(promotion.getFinalAmount());
        orderRepository.save(order);

        // P3: write the OrderCreated event into the transactional outbox, in the
        // same DB transaction as the order. OutboxRelay delivers it to Kafka later.
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("orderId", order.getId());
        event.put("userId", order.getUserId());
        event.put("amount", order.getAmount());
        event.put("discount", order.getDiscount());
        event.put("finalAmount", order.getFinalAmount());
        event.put("status", order.getStatus());
        if (order.getCreatedAt() != null) {
            event.put("createdAt", order.getCreatedAt().toString());
        }
        outboxService.saveEvent("Order", order.getId(), "OrderCreated", event);

        return new OrderWithPromotion(toResponse(order), toView(request.getAmount(), promotion));
    }

    private int reserveWithRetry(String stockId, int requiredStock) {
        int attempts = 0;
        while (true) {
            try {
                if (lockService != null) {
                    return lockService.runWithLock("stock:" + stockId,
                            () -> inventoryService.reserve(stockId, requiredStock));
                }
                return inventoryService.reserve(stockId, requiredStock);
            } catch (OptimisticLockingFailureException ex) {
                if (++attempts >= MAX_RETRIES) {
                    throw new IllegalStateException(
                            "Stock reservation failed after " + attempts + " retries (concurrent contention)", ex);
                }
            }
        }
    }

    @Cacheable(cacheNames = "orders", key = "#id")
    public OrderResponse getOrder(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        return toResponse(order);
    }

    @Cacheable(cacheNames = "orders-all")
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getAmount(),
                order.getDiscount(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    private PromotionView toView(BigDecimal originalAmount, PromotionResult result) {
        return new PromotionView(
                originalAmount,
                result.getDiscount(),
                result.getFinalAmount(),
                normalizeRule(result.getRule())
        );
    }

    /** Map internal rule codes to the frontend's rule keys. */
    private String normalizeRule(String rule) {
        return switch (rule) {
            case "NEW_USER_FIRST_ORDER_MINUS_10" -> "new_user";
            case "FULL_REDUCTION_100_MINUS_20" -> "full_reduction";
            default -> "none";
        };
    }
}
