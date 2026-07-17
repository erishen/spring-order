package com.example.order;

import com.example.order.service.OrderService;
import com.example.order.service.RedisLockService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end check that the `redis` profile wires up the distributed lock and
 * cache beans, and that the lock actually serializes concurrent work on the
 * same key. Requires a real Redis on localhost:6379; when none is reachable
 * (e.g. CI sandbox without Docker) the whole class is skipped via assumeTrue.
 *
 * Run locally with:
 *   docker compose up -d redis
 *   mvn -o test -Dtest=RedisP1IntegrationTest
 */
@SpringBootTest
@ActiveProfiles("redis")
class RedisP1IntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisLockService redisLockService;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(isRedisReachable(),
                "Redis not reachable on localhost:6379 — skipping P1 Redis integration test (start it via `docker compose up -d redis`)");
    }

    @Test
    void contextLoadsWithRedisBeans() {
        assertNotNull(orderService);
        assertNotNull(redisLockService, "RedisLockService must be present under the `redis` profile");
    }

    @Test
    void lock_serializesConcurrentWorkOnSameKey() throws InterruptedException {
        String key = "stock:integration-" + System.nanoTime();
        AtomicBoolean inProgress = new AtomicBoolean(false);
        StringBuilder overlap = new StringBuilder();

        Runnable task = () -> redisLockService.runWithLock(key, () -> {
            if (inProgress.compareAndSet(false, true)) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                inProgress.set(false);
            } else {
                overlap.append("overlap");
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(task);
        pool.submit(task);
        pool.shutdown();
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS), "tasks finished in time");

        assertTrue(overlap.length() == 0, "distributed lock should have prevented overlap");
    }

    private static boolean isRedisReachable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
