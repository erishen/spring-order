package com.example.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisLockService lockService;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        lockService = new RedisLockService(redis);
    }

    @Test
    void runWithLock_acquiresAndReleases() {
        when(valueOps.setIfAbsent(eq("lock:stock:DEFAULT"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redis.execute(any(), anyList(), any())).thenReturn(1L);

        String result = lockService.runWithLock("stock:DEFAULT", () -> "ok");

        assertEquals("ok", result);
        verify(valueOps).setIfAbsent(eq("lock:stock:DEFAULT"), anyString(), any(Duration.class));
        verify(redis).execute(any(), eq(List.of("lock:stock:DEFAULT")), any());
    }

    @Test
    void runWithLock_releasesEvenOnActionException() {
        when(valueOps.setIfAbsent(eq("lock:stock:X"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redis.execute(any(), anyList(), any())).thenReturn(1L);

        RuntimeException boom = new RuntimeException("boom");
        RuntimeException caught = null;
        try {
            lockService.runWithLock("stock:X", () -> {
                throw boom;
            });
        } catch (RuntimeException e) {
            caught = e;
        }
        assertEquals(boom, caught, "action exception must propagate");
        // unlock must still run in the finally block
        verify(redis).execute(any(), eq(List.of("lock:stock:X")), any());
    }

    @Test
    void runWithLock_failsToAcquire_degradesToDirectExecution() {
        // The lock is never granted -> after max attempts the action runs without the lock.
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        String result = lockService.runWithLock("stock:Y", () -> "done");

        assertEquals("done", result, "degraded path still executes the action");
        verify(valueOps, times(5)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        // never acquired -> unlock Lua script is never sent
        verify(redis, never()).execute(any(), anyList(), any());
    }
}
