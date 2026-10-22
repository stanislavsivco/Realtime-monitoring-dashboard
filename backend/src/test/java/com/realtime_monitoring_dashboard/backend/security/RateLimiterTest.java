package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);

    private RateLimiter limiter(int maxRequests, int maxKeys) {
        return new RateLimiter(maxRequests, Duration.ofMinutes(1), maxKeys, now::get);
    }

    @Test
    void allowsRequestsUpToTheLimit() {
        RateLimiter limiter = limiter(3, 100);

        assertThat(limiter.tryAcquire("1.1.1.1")).isZero();
        assertThat(limiter.tryAcquire("1.1.1.1")).isZero();
        assertThat(limiter.tryAcquire("1.1.1.1")).isZero();
    }

    @Test
    void blocksTheNextRequestAndSaysHowLongToWait() {
        RateLimiter limiter = limiter(2, 100);
        limiter.tryAcquire("1.1.1.1");
        limiter.tryAcquire("1.1.1.1");

        now.addAndGet(20_000);

        assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(40);
    }

    @Test
    void retryAfterIsNeverZeroWhenBlocked() {
        RateLimiter limiter = limiter(1, 100);
        limiter.tryAcquire("1.1.1.1");

        now.addAndGet(59_999);

        assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(1);
    }

    @Test
    void allowsAgainAfterTheWindowEnds() {
        RateLimiter limiter = limiter(1, 100);
        limiter.tryAcquire("1.1.1.1");
        assertThat(limiter.tryAcquire("1.1.1.1")).isPositive();

        now.addAndGet(60_000);

        assertThat(limiter.tryAcquire("1.1.1.1")).isZero();
    }

    @Test
    void blockedAttemptsDoNotExtendTheWindow() {
        RateLimiter limiter = limiter(1, 100);
        limiter.tryAcquire("1.1.1.1");
        for (int i = 0; i < 50; i++) {
            now.addAndGet(1_000);
            limiter.tryAcquire("1.1.1.1");
        }

        now.addAndGet(10_000);

        assertThat(limiter.tryAcquire("1.1.1.1")).isZero();
    }

    @Test
    void differentAddressesHaveSeparateLimits() {
        RateLimiter limiter = limiter(1, 100);
        limiter.tryAcquire("1.1.1.1");

        assertThat(limiter.tryAcquire("1.1.1.1")).isPositive();
        assertThat(limiter.tryAcquire("2.2.2.2")).isZero();
    }

    @Test
    void expiredEntriesAreRemovedSoMemoryDoesNotGrowForever() {
        RateLimiter limiter = limiter(5, 1_000);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("10.0.0." + i);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(100);

        now.addAndGet(60_000);
        limiter.tryAcquire("10.0.1.1");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void whenTooManyAddressesAreTrackedNewOnesAreBlockedButKnownOnesStillWork() {
        RateLimiter limiter = limiter(5, 3);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("c");

        assertThat(limiter.tryAcquire("d")).isPositive();
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.trackedKeys()).isEqualTo(3);
    }

    @Test
    void concurrentRequestsNeverExceedTheLimit() throws InterruptedException {
        RateLimiter limiter = limiter(10, 100);
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);

        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                start.await();
                if (limiter.tryAcquire("1.1.1.1") == 0) {
                    allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RateLimiter(5, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
