package com.example.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Distributed lock backed by Redis (SET key token NX PX + Lua-atomic unlock).
 *
 * Used to serialize stock deduction for the same {@code stockId} across multiple
 * spring-order instances, cutting down on optimistic-lock retries under contention.
 * On a single instance with no Redis this bean is simply never created (see
 * {@link ConditionalOnProperty}), and callers fall back to the DB optimistic lock.
 *
 * The unlock script only deletes the key when the held token still matches, so a
 * lock that expired in Redis cannot be released by the wrong owner (which would
 * otherwise free a lock held by another instance).
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisLockService {

    private final StringRedisTemplate redis;
    private final Duration defaultTtl = Duration.ofSeconds(3);
    private final int maxAcquireAttempts = 5;
    private final long acquireSpinMillis = 20;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Autowired
    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Acquire a lock for {@code key}, run {@code action}, then release the lock.
     * If the lock cannot be acquired after a few quick spins, the action is run
     * WITHOUT the lock (degraded path) — the DB {@code @Version} optimistic lock
     * still guarantees no oversell, just with more retries.
     */
    public <T> T runWithLock(String key, Supplier<T> action) {
        String lockKey = "lock:" + key;
        String token = UUID.randomUUID().toString();

        int attempts = 0;
        while (!redis.opsForValue().setIfAbsent(lockKey, token, defaultTtl)) {
            if (++attempts >= maxAcquireAttempts) {
                // Could not acquire in time: degrade to direct execution.
                return action.get();
            }
            try {
                Thread.sleep(acquireSpinMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return action.get();
            }
        }

        try {
            return action.get();
        } finally {
            redis.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        }
    }

    /** Non-result overload for void actions. */
    public void runWithLock(String key, Runnable action) {
        runWithLock(key, () -> {
            action.run();
            return null;
        });
    }
}
