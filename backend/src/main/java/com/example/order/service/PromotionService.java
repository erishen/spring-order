package com.example.order.service;

import com.example.order.dto.PromotionResult;
import com.example.order.exception.InsufficientStockException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PromotionService {

    private static final BigDecimal NEW_USER_DISCOUNT = BigDecimal.TEN;
    private static final BigDecimal FULL_REDUCTION_THRESHOLD = BigDecimal.valueOf(100);
    private static final BigDecimal FULL_REDUCTION_DISCOUNT = BigDecimal.valueOf(20);

    /**
     *  促销规则
     * Rule 1: 新用户首单 -10
     * Rule 2: 订单 >= 100, -20
     * Rule 3: 不可叠加，满减优先
     * Rule 4: 库存不足 → InsufficientStockException
     */
    public PromotionResult applyPromotion(BigDecimal amount, boolean isNewUser, int requiredStock, int availableStock) {
        // Rule 4: 库存校验
        if (requiredStock > availableStock) {
            throw new InsufficientStockException(
                    "Insufficient stock: required=" + requiredStock + ", available=" + availableStock);
        }

        // 边界：amount <= 0 直接返回无折扣
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionResult.noDiscount(amount != null ? amount : BigDecimal.ZERO);
        }

        // Rule 3: 不可叠加，满减优先
        // Rule 2: 满 100 减 20
        if (amount.compareTo(FULL_REDUCTION_THRESHOLD) >= 0) {
            BigDecimal finalAmount = amount.subtract(FULL_REDUCTION_DISCOUNT);
            return PromotionResult.of(FULL_REDUCTION_DISCOUNT, "FULL_REDUCTION_100_MINUS_20", finalAmount);
        }

        // Rule 1: 新用户首单 -10
        if (isNewUser) {
            BigDecimal finalAmount = amount.subtract(NEW_USER_DISCOUNT);
            // 确保不出现负数
            if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                finalAmount = BigDecimal.ZERO;
            }
            return PromotionResult.of(NEW_USER_DISCOUNT, "NEW_USER_FIRST_ORDER_MINUS_10", finalAmount);
        }

        return PromotionResult.noDiscount(amount);
    }
}
