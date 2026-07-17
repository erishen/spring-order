package com.example.order.service;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderWithPromotion;
import com.example.order.dto.PromotionResult;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PromotionService promotionService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_normal_returnsResponse() {
        OrderCreateRequest request = new OrderCreateRequest("user1", new BigDecimal("50.00"));
        when(orderRepository.existsByUserId(anyString())).thenReturn(false);
        when(inventoryService.reserve(anyString(), anyInt())).thenReturn(1000);
        when(promotionService.applyPromotion(any(BigDecimal.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PromotionResult(BigDecimal.ZERO, "NO_DISCOUNT", new BigDecimal("50.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderWithPromotion response = orderService.createOrder(request);

        assertNotNull(response.getOrder().getId());
        assertEquals("user1", response.getOrder().getUserId());
        assertEquals(new BigDecimal("50.00"), response.getOrder().getAmount());
        assertEquals(BigDecimal.ZERO, response.getOrder().getDiscount());
        assertEquals(new BigDecimal("50.00"), response.getOrder().getFinalAmount());
        assertEquals("CREATED", response.getOrder().getStatus());
        assertEquals(new BigDecimal("50.00"), response.getPromotion().getOriginalAmount());
        assertEquals("none", response.getPromotion().getAppliedRule());
    }

    @Test
    void createOrder_withPromotion_appliesDiscount() {
        OrderCreateRequest request = new OrderCreateRequest("user1", new BigDecimal("100.00"));
        when(orderRepository.existsByUserId(anyString())).thenReturn(false);
        when(inventoryService.reserve(anyString(), anyInt())).thenReturn(1000);
        when(promotionService.applyPromotion(any(BigDecimal.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PromotionResult(new BigDecimal("20"), "FULL_REDUCTION_100_MINUS_20", new BigDecimal("80.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderWithPromotion response = orderService.createOrder(request);

        assertEquals(new BigDecimal("20"), response.getOrder().getDiscount());
        assertEquals(new BigDecimal("80.00"), response.getOrder().getFinalAmount());
        assertEquals("full_reduction", response.getPromotion().getAppliedRule());
        assertEquals(new BigDecimal("80.00"), response.getPromotion().getFinalPrice());
    }

    @Test
    void createOrder_firstOrderForUser_treatedAsNewUser() {
        OrderCreateRequest request = new OrderCreateRequest("newbie", new BigDecimal("50"));
        when(orderRepository.existsByUserId("newbie")).thenReturn(false);
        when(inventoryService.reserve(anyString(), anyInt())).thenReturn(1000);
        when(promotionService.applyPromotion(any(BigDecimal.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(PromotionResult.noDiscount(new BigDecimal("50")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(request);

        verify(promotionService).applyPromotion(eq(new BigDecimal("50")), eq(true), anyInt(), anyInt());
    }

    @Test
    void createOrder_secondOrderForUser_notNewUser() {
        when(orderRepository.existsByUserId("repeat")).thenReturn(false, true);
        when(inventoryService.reserve(anyString(), anyInt())).thenReturn(1000);
        when(promotionService.applyPromotion(any(BigDecimal.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(PromotionResult.noDiscount(new BigDecimal("50")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(new OrderCreateRequest("repeat", new BigDecimal("50")));
        orderService.createOrder(new OrderCreateRequest("repeat", new BigDecimal("50")));

        verify(promotionService).applyPromotion(eq(new BigDecimal("50")), eq(false), anyInt(), anyInt());
    }

    @Test
    void getOrder_notFound_throwsOrderNotFound() {
        when(orderRepository.findById("nonexistent")).thenReturn(java.util.Optional.empty());
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder("nonexistent"));
    }

    @Test
    void getAllOrders_returnsAll() {
        OrderCreateRequest request1 = new OrderCreateRequest("user1", new BigDecimal("50.00"));
        OrderCreateRequest request2 = new OrderCreateRequest("user2", new BigDecimal("100.00"));
        when(orderRepository.existsByUserId(anyString())).thenReturn(false);
        when(inventoryService.reserve(anyString(), anyInt())).thenReturn(1000);
        when(promotionService.applyPromotion(any(BigDecimal.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PromotionResult(BigDecimal.ZERO, "NO_DISCOUNT", new BigDecimal("50.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(UUID.randomUUID().toString());
            return o;
        });

        Order saved1 = new Order("user1", new BigDecimal("50.00")); saved1.setId("o1");
        Order saved2 = new Order("user2", new BigDecimal("100.00")); saved2.setId("o2");
        when(orderRepository.findAll()).thenReturn(List.of(saved1, saved2));

        orderService.createOrder(request1);
        orderService.createOrder(request2);

        assertEquals(2, orderService.getAllOrders().size());
    }
}
