package com.example.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Durable copy of the read-model projection row produced by {@link OrderProjection}.
 *
 * <p>The projection is rebuilt in-memory on startup from this table, so it
 * survives a backend restart (the dev datasource is a file-based H2). The
 * natural key is {@code order_id}; the upstream consumer de-duplicates by
 * orderId before writing, so a row is inserted at most once.
 */
@Entity
@Table(name = "consumed_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumedOrderEntity {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "discount", precision = 19, scale = 2)
    private BigDecimal discount;

    @Column(name = "final_amount", precision = 19, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "status")
    private String status;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
}
