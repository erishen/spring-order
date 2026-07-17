package com.example.order.controller;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderWithPromotion;
import com.example.order.dto.PromotionView;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrder_success_returns201() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest("user1", new BigDecimal("50.00"));
        OrderResponse order = new OrderResponse(
                "order-1", "user1", new BigDecimal("50.00"),
                BigDecimal.ZERO, new BigDecimal("50.00"), "CREATED", LocalDateTime.now());
        PromotionView promotion = new PromotionView(
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"), "none");
        OrderWithPromotion response = new OrderWithPromotion(order, promotion);

        when(orderService.createOrder(any(OrderCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.id").value("order-1"))
                .andExpect(jsonPath("$.order.userId").value("user1"))
                .andExpect(jsonPath("$.order.amount").value(50.00))
                .andExpect(jsonPath("$.promotion.originalAmount").value(50.00))
                .andExpect(jsonPath("$.promotion.appliedRule").value("none"));
    }

    @Test
    void createOrder_invalidAmount_returns400() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest("user1", new BigDecimal("-10"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("amount must be positive"));
    }

    @Test
    void createOrder_emptyUserId_returns400() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest("", new BigDecimal("50.00"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").value("userId must not be blank"));
    }

    @Test
    void createOrder_nullUserId_returns400() throws Exception {
        String json = "{\"amount\": 50.00}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").value("userId must not be blank"));
    }

    @Test
    void getOrder_returnsOrder() throws Exception {
        OrderResponse response = new OrderResponse(
                "order-1", "user1", new BigDecimal("50.00"),
                BigDecimal.ZERO, new BigDecimal("50.00"), "CREATED", LocalDateTime.now());

        when(orderService.getOrder("order-1")).thenReturn(response);

        mockMvc.perform(get("/api/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-1"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        when(orderService.getOrder("missing"))
                .thenThrow(new com.example.order.exception.OrderNotFoundException("Order not found"));

        mockMvc.perform(get("/api/orders/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void getAllOrders_returnsList() throws Exception {
        OrderResponse response = new OrderResponse(
                "order-1", "user1", new BigDecimal("50.00"),
                BigDecimal.ZERO, new BigDecimal("50.00"), "CREATED", LocalDateTime.now());

        when(orderService.getAllOrders()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-1"));
    }
}
