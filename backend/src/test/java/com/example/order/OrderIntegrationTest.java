package com.example.order;

import com.example.order.model.Inventory;
import com.example.order.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack wiring test: controller -> real OrderService -> real
 * PromotionService -> real JPA repositories + Flyway-seeded inventory.
 * Exercises the genuine promotion behaviour end-to-end against a real
 * (in-memory H2) database, and proves stock is reserved from the inventory
 * table (Rule 4) rather than hard-coded in the controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    private String body(Map<String, Object> m) throws Exception {
        return objectMapper.writeValueAsString(m);
    }

    @Test
    void newUser_firstOrderBelow100_gets10Discount() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", "int-new-user-a", "amount", 50))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promotion.appliedRule").value("new_user"))
                .andExpect(jsonPath("$.promotion.discount").value(10))
                .andExpect(jsonPath("$.promotion.finalPrice").value(40));
    }

    @Test
    void repeatUser_secondOrderBelow100_getsNoDiscount() throws Exception {
        String userId = "int-repeat-user";
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", userId, "amount", 50))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", userId, "amount", 50))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promotion.appliedRule").value("none"))
                .andExpect(jsonPath("$.promotion.discount").value(0));
    }

    @Test
    void newUser_amountAbove100_fullReductionTakesPriority() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", "int-new-user-b", "amount", 150))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promotion.appliedRule").value("full_reduction"))
                .andExpect(jsonPath("$.promotion.discount").value(20))
                .andExpect(jsonPath("$.promotion.finalPrice").value(130));
    }

    @Test
    void insufficientStock_returns400() throws Exception {
        // Seed a dedicated low-stock pool, then ask for more than it holds.
        inventoryRepository.save(new Inventory("low", 3, 0L));

        Map<String, Object> req = Map.of(
                "userId", "int-stock-user",
                "amount", 100,
                "stockId", "low",
                "requiredStock", 5);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient Stock"));
    }

    @Test
    void getOrder_missing_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createOrder_thenFetchById_roundTrips() throws Exception {
        String location = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", "int-roundtrip", "amount", 200))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("int-roundtrip"));
    }
}
