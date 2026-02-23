package com.example.rewards.config;

import com.example.rewards.common.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitService {

    private static final long CLEANUP_INTERVAL_MS = 60_000L;
    private static final long RETENTION_MS = 3_600_000L;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private volatile long lastCleanupAtMs = 0L;

    public void assertAllowed(String key, int maxRequests, Duration window, String message) {
        if (maxRequests <= 0) {
            return;
        }
        if (!allow(key, maxRequests, window)) {
            throw new TooManyRequestsException(message);
        }
    }

    private boolean allow(String key, int maxRequests, Duration window) {
        long nowMs = System.currentTimeMillis();
        long windowMs = window.toMillis();

        Counter counter = counters.compute(key, (k, current) -> {
            if (current == null || (nowMs - current.windowStartMs) >= windowMs) {
                return new Counter(nowMs, 1);
            }
            current.count += 1;
            return current;
        });

        maybeCleanup(nowMs);
        return counter.count <= maxRequests;
    }

    private void maybeCleanup(long nowMs) {
        if ((nowMs - lastCleanupAtMs) < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupAtMs = nowMs;
        counters.entrySet().removeIf(entry -> (nowMs - entry.getValue().windowStartMs) > RETENTION_MS);
    }

    private static class Counter {
        private long windowStartMs;
        private int count;

        private Counter(long windowStartMs, int count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
