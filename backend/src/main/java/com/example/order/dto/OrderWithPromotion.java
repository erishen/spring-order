package com.example.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope returned by POST /api/orders so the client receives both the created
 * order and the promotion that was applied in a single response — matching the
 * frontend's {@code { order, promotion }} contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderWithPromotion {

    private OrderResponse order;
    private PromotionView promotion;
}
