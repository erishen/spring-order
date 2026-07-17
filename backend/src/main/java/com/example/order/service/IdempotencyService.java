package com.example.order.service;

import com.example.order.dto.OrderWithPromotion;
import com.example.order.model.IdempotencyRecord;
import com.example.order.model.IdempotencyStatus;
import com.example.order.repository.IdempotencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Idempotency-Key backing store (DB-backed, zero external infra).
 *
 * Flow in the controller:
 *   1. checkAndReserve(key)
 *        - HIT         -> a prior attempt already COMPLETED; replay cached response (200)
 *        - IN_PROGRESS -> another request is processing the same key; caller returns 409
 *        - PROCEED     -> this request owns the key
 *   2. on success  -> complete(key, response)  (persist response for future replays)
 *   3. on failure  -> fail(key)                (no order was persisted, release key so client may retry)
 *
 * Uses REQUIRES_NEW so the idempotency row commits/cleans up independently of the
 * (possibly rolled-back) order transaction.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** HTTP header clients send to make a request idempotent. */
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    public enum Outcome { PROCEED, HIT, IN_PROGRESS }

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome checkAndReserve(String key) {
        Optional<IdempotencyRecord> existing = repository.findByKey(key);
        if (existing.isPresent()) {
            IdempotencyRecord r = existing.get();
            return r.getStatus() == IdempotencyStatus.COMPLETED ? Outcome.HIT : Outcome.IN_PROGRESS;
        }
        IdempotencyRecord record = new IdempotencyRecord(key, IdempotencyStatus.IN_PROGRESS);
        try {
            repository.save(record); // unique(idempotency_key) guards a concurrent same-key insert
            return Outcome.PROCEED;
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request won the insert race for this key.
            log.debug("Idempotency insert race detected for key {}", key);
            IdempotencyRecord winner = repository.findByKey(key).orElseThrow();
            return winner.getStatus() == IdempotencyStatus.COMPLETED ? Outcome.HIT : Outcome.IN_PROGRESS;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, OrderWithPromotion response) {
        IdempotencyRecord record = repository.findByKey(key).orElse(null);
        if (record == null) {
            record = new IdempotencyRecord(key, IdempotencyStatus.COMPLETED);
        }
        record.setStatus(IdempotencyStatus.COMPLETED);
        try {
            record.setResponseBody(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize idempotency response for key {}", key, e);
            record.setResponseBody(null);
        }
        repository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String key) {
        repository.findByKey(key).ifPresent(repository::delete);
    }

    public OrderWithPromotion getCached(String key) {
        return repository.findByKey(key)
                .filter(r -> r.getStatus() == IdempotencyStatus.COMPLETED && r.getResponseBody() != null)
                .map(r -> {
                    try {
                        return objectMapper.readValue(r.getResponseBody(), OrderWithPromotion.class);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize idempotency response for key {}", key, e);
                        return null;
                    }
                })
                .orElse(null);
    }
}
