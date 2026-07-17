package com.example.order;

import com.example.order.dto.OrderCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "resilience4j.ratelimiter.instances.orderCreate.limit-for-period=3",
        "resilience4j.ratelimiter.instances.orderCreate.limit-refresh-period=10s",
        "resilience4j.ratelimiter.instances.orderCreate.timeout-duration=0"
})
class RateLimitIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void orderCreate_exceedingRateLimit_returns429() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest("user-rl", new BigDecimal("50.00"));
        String body = objectMapper.writeValueAsString(request);

        // Consume the 3 permits within the refresh window.
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // The 4th request in the same window is rejected with 429 Too Many Requests.
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
