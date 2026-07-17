package com.example.order.service;

import com.example.order.dto.PromotionResult;
import com.example.order.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @InjectMocks
    private PromotionService promotionService;

    // === Rule 1: 新用户首单 -10 ===

    @Test
    void applyPromotion_newUser_amountBelow100_returns10Discount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("50"), true, 1, 10);

        assertEquals(new BigDecimal("10"), result.getDiscount());
        assertEquals("NEW_USER_FIRST_ORDER_MINUS_10", result.getRule());
        assertEquals(new BigDecimal("40"), result.getFinalAmount());
    }

    @Test
    void applyPromotion_newUser_amountBelow10_discountDoesNotGoNegative() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("5"), true, 1, 10);

        assertEquals(new BigDecimal("10"), result.getDiscount());
        assertEquals(BigDecimal.ZERO, result.getFinalAmount());
    }

    // === Rule 2: 满 100 减 20 ===

    @Test
    void applyPromotion_amountExactly100_returns20Discount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("100"), false, 1, 10);

        assertEquals(new BigDecimal("20"), result.getDiscount());
        assertEquals("FULL_REDUCTION_100_MINUS_20", result.getRule());
        assertEquals(new BigDecimal("80"), result.getFinalAmount());
    }

    @Test
    void applyPromotion_amountAbove100_returns20Discount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("150"), false, 1, 10);

        assertEquals(new BigDecimal("20"), result.getDiscount());
        assertEquals(new BigDecimal("130"), result.getFinalAmount());
    }

    // === Rule 3: 不可叠加，满减优先 ===

    @Test
    void applyPromotion_newUser_amountAbove100_fullReductionTakesPriority() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("100"), true, 1, 10);

        // 满减优先，不是新用户折扣
        assertEquals(new BigDecimal("20"), result.getDiscount());
        assertEquals("FULL_REDUCTION_100_MINUS_20", result.getRule());
    }

    @Test
    void applyPromotion_newUser_amount200_fullReductionTakesPriority() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("200"), true, 1, 10);

        assertEquals(new BigDecimal("20"), result.getDiscount());
        assertEquals("FULL_REDUCTION_100_MINUS_20", result.getRule());
    }

    // === Rule 4: 库存不足 → InsufficientStockException ===

    @Test
    void applyPromotion_insufficientStock_throwsException() {
        assertThrows(InsufficientStockException.class, () ->
                promotionService.applyPromotion(new BigDecimal("100"), false, 5, 3));
    }

    @Test
    void applyPromotion_exactStock_succeeds() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("100"), false, 5, 5);

        assertEquals(new BigDecimal("20"), result.getDiscount());
    }

    // === 边界测试 ===

    @Test
    void applyPromotion_zeroAmount_returnsNoDiscount() {
        PromotionResult result = promotionService.applyPromotion(
                BigDecimal.ZERO, false, 1, 10);

        assertEquals(BigDecimal.ZERO, result.getDiscount());
        assertEquals("NO_DISCOUNT", result.getRule());
    }

    @Test
    void applyPromotion_negativeAmount_returnsNoDiscount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("-10"), false, 1, 10);

        assertEquals(BigDecimal.ZERO, result.getDiscount());
        assertEquals("NO_DISCOUNT", result.getRule());
    }

    @Test
    void applyPromotion_amount99_notNewUser_returnsNoDiscount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("99"), false, 1, 10);

        assertEquals(BigDecimal.ZERO, result.getDiscount());
        assertEquals("NO_DISCOUNT", result.getRule());
    }

    @Test
    void applyPromotion_amount99_newUser_returnsNewUserDiscount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("99"), true, 1, 10);

        assertEquals(new BigDecimal("10"), result.getDiscount());
        assertEquals("NEW_USER_FIRST_ORDER_MINUS_10", result.getRule());
    }

    @Test
    void applyPromotion_notNewUser_amountBelow100_noDiscount() {
        PromotionResult result = promotionService.applyPromotion(
                new BigDecimal("50"), false, 1, 10);

        assertEquals(BigDecimal.ZERO, result.getDiscount());
        assertEquals("NO_DISCOUNT", result.getRule());
    }
}
