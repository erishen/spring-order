package com.example.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared stock pool. A single row per {@code stockId} (seeded with "DEFAULT"
 * in V1__init.sql). Deductions go through {@code InventoryService.reserve},
 * which is @Transactional and guarded by {@code @Version} so concurrent orders
 * cannot oversell the same stock.
 */
@Entity
@Table(name = "inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    private String stockId;

    @Column(nullable = false)
    private int available;

    @Version
    private Long version;
}
