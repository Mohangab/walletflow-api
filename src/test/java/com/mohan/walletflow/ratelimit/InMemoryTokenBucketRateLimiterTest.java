package com.mohan.walletflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class InMemoryTokenBucketRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RateLimitProperties properties = new RateLimitProperties(true, "memory", 3, 1, Duration.ofSeconds(2));
    private final InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(properties, now::get);

    @Test
    void allowsBurstUpToCapacityThenRejectsWithRetryAfter() {
        assertThat(limiter.tryConsume("u").remaining()).isEqualTo(2);
        assertThat(limiter.tryConsume("u").allowed()).isTrue();
        assertThat(limiter.tryConsume("u").allowed()).isTrue();

        RateLimiter.Decision rejected = limiter.tryConsume("u");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMillis()).isEqualTo(2000);
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("u");
        }
        now.addAndGet(2000);

        assertThat(limiter.tryConsume("u").allowed()).isTrue();
        assertThat(limiter.tryConsume("u").allowed()).isFalse();
    }

    @Test
    void bucketsAreIndependentPerKey() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("alice");
        }
        assertThat(limiter.tryConsume("alice").allowed()).isFalse();
        assertThat(limiter.tryConsume("bob").allowed()).isTrue();
    }
}
