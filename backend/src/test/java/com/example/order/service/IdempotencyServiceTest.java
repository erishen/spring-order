package com.example.order.service;

import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderWithPromotion;
import com.example.order.dto.PromotionView;
import com.example.order.model.IdempotencyRecord;
import com.example.order.model.IdempotencyStatus;
import com.example.order.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyRepository repository;
    @Mock ObjectMapper objectMapper;
    @InjectMocks IdempotencyService service;

    private OrderWithPromotion sample() {
        OrderResponse order = new OrderResponse("o1", "u1", new BigDecimal("50"),
                BigDecimal.ZERO, new BigDecimal("50"), "CREATED", LocalDateTime.now());
        PromotionView promo = new PromotionView(new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("50"), "none");
        return new OrderWithPromotion(order, promo);
    }

    @Test
    void checkAndReserve_proceedsWhenKeyFree() {
        when(repository.findByKey("k1")).thenReturn(Optional.empty());
        assertEquals(IdempotencyService.Outcome.PROCEED, service.checkAndReserve("k1"));
        verify(repository).save(any(IdempotencyRecord.class));
    }

    @Test
    void checkAndReserve_hitsWhenCompleted() {
        when(repository.findByKey("k1")).thenReturn(Optional.of(new IdempotencyRecord("k1", IdempotencyStatus.COMPLETED)));
        assertEquals(IdempotencyService.Outcome.HIT, service.checkAndReserve("k1"));
    }

    @Test
    void checkAndReserve_inProgressWhenOwnedByAnother() {
        when(repository.findByKey("k1")).thenReturn(Optional.of(new IdempotencyRecord("k1", IdempotencyStatus.IN_PROGRESS)));
        assertEquals(IdempotencyService.Outcome.IN_PROGRESS, service.checkAndReserve("k1"));
    }

    @Test
    void checkAndReserve_lostRaceResolvesToExistingState() {
        IdempotencyRecord winner = new IdempotencyRecord("k1", IdempotencyStatus.IN_PROGRESS);
        when(repository.findByKey("k1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.save(any(IdempotencyRecord.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertEquals(IdempotencyService.Outcome.IN_PROGRESS, service.checkAndReserve("k1"));
    }

    @Test
    void complete_persistsSerializedResponseAsCompleted() throws Exception {
        OrderWithPromotion resp = sample();
        when(objectMapper.writeValueAsString(resp)).thenReturn("{\"json\":\"x\"}");
        service.complete("k1", resp);
        verify(repository).save(argThat(r ->
                r.getStatus() == IdempotencyStatus.COMPLETED && "{\"json\":\"x\"}".equals(r.getResponseBody())));
    }

    @Test
    void fail_deletesTheReservation() {
        IdempotencyRecord rec = new IdempotencyRecord("k1", IdempotencyStatus.IN_PROGRESS);
        when(repository.findByKey("k1")).thenReturn(Optional.of(rec));
        service.fail("k1");
        verify(repository).delete(rec);
    }

    @Test
    void getCached_replaysCompletedResponse() throws Exception {
        IdempotencyRecord rec = new IdempotencyRecord("k1", IdempotencyStatus.COMPLETED);
        rec.setResponseBody("{\"json\":\"x\"}");
        when(repository.findByKey("k1")).thenReturn(Optional.of(rec));
        OrderWithPromotion expected = sample();
        when(objectMapper.readValue("{\"json\":\"x\"}", OrderWithPromotion.class)).thenReturn(expected);
        assertEquals(expected, service.getCached("k1"));
    }
}
