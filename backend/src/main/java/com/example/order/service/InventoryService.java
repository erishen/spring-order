package com.example.order.service;

import com.example.order.exception.InsufficientStockException;
import com.example.order.model.Inventory;
import com.example.order.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place that mutates shared stock. Each {@code reserve} runs in its own
 * transaction (this is a separate @Service bean so callers get a fresh
 * transaction on every retry). The {@code @Version} column makes the UPDATE
 * atomic: if another writer committed in between, Hibernate throws
 * OptimisticLockingFailureException and the caller retries.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * @return the available amount BEFORE this deduction (so the caller can use
     *         it for promotion messaging), or throws if stock is insufficient.
     */
    @Transactional
    public int reserve(String stockId, int required) {
        Inventory inv = inventoryRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stock: " + stockId));

        if (required > inv.getAvailable()) {
            throw new InsufficientStockException(
                    "Insufficient stock: required=" + required + ", available=" + inv.getAvailable());
        }

        inv.setAvailable(inv.getAvailable() - required);
        inventoryRepository.save(inv);

        // available amount before this deduction
        return inv.getAvailable() + required;
    }
}
