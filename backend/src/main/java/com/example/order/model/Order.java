package com.example.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal discount;

    @Column(precision = 19, scale = 2)
    private BigDecimal finalAmount;

    private String status;

    private LocalDateTime createdAt;

    // Optimistic locking: Hibernate bumps this on every UPDATE and fails the
    // transaction if a concurrent writer changed the row underneath us.
    @Version
    private Long version;

    public Order(String userId, BigDecimal amount) {
        this.userId = userId;
        this.amount = amount;
        this.status = "CREATED";
        this.createdAt = LocalDateTime.now();
    }
}
