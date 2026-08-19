package com.example.order.service;

import com.example.order.model.ConsumedOrderEntity;
import com.example.order.repository.ConsumedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-model projection built by the Kafka consumer ({@link OrderEventLogger}).
 * In a real system this would be a separate materialized view (its own table /
 * cache / Elasticsearch index) maintained by a downstream service; here it is a
 * process-local projection so the runtime-status page can show that "an event
 * was consumed and produced new state".
 *
 * <p>The projection is mirrored to the shared {@code consumed_orders} table on
 * every consume. The <b>read path</b> ({@link #getRecent()}, {@link
 * #getTotalConsumed()}, {@link #getLastConsumedAt()}, {@link #isConsumed})
 * always queries that table directly, so in a multi-replica deployment (e.g.
 * {@code dev-full}) every replica serves identical data and a {@code reset()}
 * on any one replica — which deletes the shared rows — makes the projection
 * empty for <em>all</em> replicas. {@link #reset()} also clears the in-memory
 * dedup set, keeping it consistent with the order/outbox "清理" action.
 *
 * <p>Consumption is idempotent: the Outbox relay delivers at-least-once, so
 * the same OrderCreated may arrive more than once. We dedupe by orderId, which
 * pairs with the P2 Idempotency-Key on the write path. The dedup set is
 * per-instance (each replica consumes disjoint partitions) and rehydrated from
 * the table on startup.
 */
@Component
public class OrderProjection {

    private static final Logger log = LoggerFactory.getLogger(OrderProjection.class);

    private final ConsumedOrderRepository repository;

    /** 已处理的 orderId 集合，用于消费端幂等去重（每实例维护即可，不跨副本共享）。 */
    private final Set<String> seenOrderIds = ConcurrentHashMap.newKeySet();

    public OrderProjection(ConsumedOrderRepository repository) {
        this.repository = repository;
    }

    /**
     * 消费一条 OrderCreated 事件，落库共享表 consumed_orders（持久化，重启可重建）。
     * 读路径统一从表取数，因此多副本下任意副本清理后所有副本读到空，投影保持一致。
     *
     * @return true 表示首次消费并写入投影；false 表示幂等跳过（已处理过）。
     */
    public boolean consume(String orderId, String userId, BigDecimal amount,
                           BigDecimal discount, BigDecimal finalAmount,
                           String status, LocalDateTime consumedAt) {
        if (orderId == null || !seenOrderIds.add(orderId)) {
            return false; // 已处理过，幂等跳过
        }
        repository.save(new ConsumedOrderEntity(orderId, userId, amount, discount, finalAmount, status, consumedAt));
        return true;
    }

    /** 读路径统一走共享表 consumed_orders，使任意副本清理后所有副本读到空（多副本一致）。 */
    public long getTotalConsumed() {
        return repository.count();
    }

    public LocalDateTime getLastConsumedAt() {
        return repository.findTopByOrderByConsumedAtDesc()
                .map(ConsumedOrderEntity::getConsumedAt)
                .orElse(null);
    }

    public List<ConsumedOrder> getRecent() {
        return repository.findTop20ByOrderByConsumedAtDesc().stream()
                .map(e -> new ConsumedOrder(e.getOrderId(), e.getUserId(), e.getAmount(),
                        e.getDiscount(), e.getFinalAmount(), e.getStatus(), e.getConsumedAt()))
                .toList();
    }

    public boolean isConsumed(String orderId) {
        return seenOrderIds.contains(orderId) || repository.existsById(orderId);
    }

    /** 清空整个投影（用于演示数据重置，与订单/Outbox 表一并清理）。共享表被清后所有副本读空。 */
    public void reset() {
        seenOrderIds.clear();
        repository.deleteAll();
    }

    /**
     * 启动时从 consumed_orders 表重建幂等去重集合，使消费端在后端重启后仍知道哪些
     * orderId 已处理（避免重复投影）。读路径实时查共享表，故重启后投影自然与库一致。
     */
    @PostConstruct
    public void rehydrate() {
        try {
            for (ConsumedOrderEntity e : repository.findAll()) {
                seenOrderIds.add(e.getOrderId());
            }
            log.info("Rehydrated read-model projection dedup set from {} consumed order(s)", seenOrderIds.size());
        } catch (Exception ex) {
            // 极端情况（如表尚不存在）下不阻断启动
            log.warn("Projection rehydrate skipped: {}", ex.getMessage());
        }
    }

    /** 投影中的一条已消费订单（字段以字符串存储，直接用于 UI 展示）。 */
    public static class ConsumedOrder {
        public final String orderId;
        public final String userId;
        public final String amount;
        public final String discount;
        public final String finalAmount;
        public final String status;
        public final String consumedAt;

        public ConsumedOrder(String orderId, String userId, BigDecimal amount,
                             BigDecimal discount, BigDecimal finalAmount,
                             String status, LocalDateTime consumedAt) {
            this.orderId = orderId;
            this.userId = userId;
            this.amount = amount == null ? null : amount.toPlainString();
            this.discount = discount == null ? null : discount.toPlainString();
            this.finalAmount = finalAmount == null ? null : finalAmount.toPlainString();
            this.status = status;
            this.consumedAt = consumedAt == null ? null : consumedAt.toString();
        }
    }
}
