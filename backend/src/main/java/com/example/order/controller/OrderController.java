package com.example.order.controller;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderWithPromotion;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderWithPromotion> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderWithPromotion response = orderService.createOrder(request);
        return ResponseEntity
                .created(URI.create("/api/orders/" + response.getOrder().getId()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {
        OrderResponse response = orderService.getOrder(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> responses = orderService.getAllOrders();
        return ResponseEntity.ok(responses);
    }
}
