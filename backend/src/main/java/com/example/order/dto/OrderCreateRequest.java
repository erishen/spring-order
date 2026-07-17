package com.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotBlank(message = "userId must not be blank")
    private String userId;

    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /**
     * Optional inventory levers exposed through the public API.
     * - stockId: which shared stock pool to draw from (default "DEFAULT", seeded in V1).
     * - requiredStock: how many units this order consumes (default 1).
     * Available stock is now server-side state (inventory table), no longer sent by the client.
     */
    private String stockId;

    private Integer requiredStock;

    /** Convenience constructor kept for the common userId+amount case. */
    public OrderCreateRequest(String userId, BigDecimal amount) {
        this.userId = userId;
        this.amount = amount;
    }
}
