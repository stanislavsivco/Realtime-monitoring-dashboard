package com.realtime_monitoring_dashboard.backend.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class RateLimiter {

    static final int DEFAULT_MAX_KEYS = 50_000;

    private final int maxRequests;
    private final long windowMillis;
    private final int maxKeys;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep;

    private record Window(long start, int count) {
    }

    public RateLimiter(int maxRequests, Duration window) {
        this(maxRequests, window, DEFAULT_MAX_KEYS, System::currentTimeMillis);
    }

    RateLimiter(int maxRequests, Duration window, int maxKeys, LongSupplier clock) {
        if (maxRequests < 1) {
            throw new IllegalStateException("Rate limit must allow at least 1 request");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Rate limit window must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
        this.clock = clock;
        this.lastSweep = new AtomicLong(clock.getAsLong());
    }

    public long tryAcquire(String key) {
        long now = clock.getAsLong();
        sweepIfDue(now);

        if (!windows.containsKey(key) && windows.size() >= maxKeys) {
            removeExpired(now);
            if (windows.size() >= maxKeys) {
                return toSeconds(windowMillis);
            }
        }

        Window window = windows.compute(key, (k, current) -> {
            if (current == null || now - current.start() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(current.start(), Math.min(current.count() + 1, maxRequests + 1));
        });

        if (window.count() <= maxRequests) {
            return 0;
        }
        return toSeconds(window.start() + windowMillis - now);
    }

    int trackedKeys() {
        return windows.size();
    }

    private void sweepIfDue(long now) {
        long previous = lastSweep.get();
        if (now - previous >= windowMillis && lastSweep.compareAndSet(previous, now)) {
            removeExpired(now);
        }
    }

    private void removeExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= windowMillis);
    }

    private static long toSeconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }
}
