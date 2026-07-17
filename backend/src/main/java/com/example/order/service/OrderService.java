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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final int MAX_RETRIES = 5;

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PromotionService promotionService;

    @Autowired(required = false)
    private RedisLockService lockService;

    public OrderService(OrderRepository orderRepository,
                        InventoryService inventoryService,
                        PromotionService promotionService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.promotionService = promotionService;
    }

    /**
     * Creates an order backed by JPA (no more in-memory map). Stock is reserved
     * through {@link InventoryService}, which is optimistic-lock guarded so
     * concurrent orders cannot oversell the same pool. When Redis is available,
     * the deduction is also serialized by a distributed lock (per stockId) to
     * reduce optimistic-lock retry storms under multi-instance contention.
     */
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
