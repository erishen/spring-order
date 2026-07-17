package com.example.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API-facing promotion payload. Field names/rule codes are aligned with the
 * frontend contract ({@code originalAmount / discount / finalPrice /
 * appliedRule}) so the client renders correctly against the real backend
 * (not just the MSW mock).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionView {

    private BigDecimal originalAmount;
    private BigDecimal discount;
    private BigDecimal finalPrice;
    private String appliedRule; // new_user | full_reduction | none
}
