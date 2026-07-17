package com.example.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String userId;
    private BigDecimal amount;
    private BigDecimal discount;
    private BigDecimal finalAmount;
    private String status;
    private LocalDateTime createdAt;
}
