package com.example.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResult {

    private BigDecimal discount;
    private String rule;
    private BigDecimal finalAmount;

    public static PromotionResult noDiscount(BigDecimal originalAmount) {
        return new PromotionResult(BigDecimal.ZERO, "NO_DISCOUNT", originalAmount);
    }

    public static PromotionResult of(BigDecimal discount, String rule, BigDecimal finalAmount) {
        return new PromotionResult(discount, rule, finalAmount);
    }
}
