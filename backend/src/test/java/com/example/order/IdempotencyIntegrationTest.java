package com.example.order;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InventoryRepository inventoryRepository;

    @Test
    void sameIdempotencyKey_replaysCachedResponseAndChargesStockExactlyOnce() throws Exception {
        int before = inventoryRepository.findById("DEFAULT").orElseThrow().getAvailable();

        OrderCreateRequest request = new OrderCreateRequest("user-replay", new BigDecimal("50.00"));
        String body = objectMapper.writeValueAsString(request);

        // First attempt -> 201 Created, a real order is persisted and stock is deducted.
        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second attempt with the same key -> 200 OK, replayed from the idempotency store.
        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Stock must have been charged exactly once for the repeated key.
        int after = inventoryRepository.findById("DEFAULT").orElseThrow().getAvailable();
        assertEquals(before - 1, after, "stock should be deducted only once for a repeated key");

        // Exactly one order is persisted for this user (no duplicate from the replay).
        MvcResult listResult = mvc.perform(get("/api/orders")).andReturn();
        String listJson = listResult.getResponse().getContentAsString();
        List<?> orders = objectMapper.readValue(listJson, List.class);
        long count = orders.stream()
                .filter(o -> "user-replay".equals(((Map<?, ?>) o).get("userId")))
                .count();
        assertEquals(1, count, "a repeated idempotency key must not create a duplicate order");
    }
}
