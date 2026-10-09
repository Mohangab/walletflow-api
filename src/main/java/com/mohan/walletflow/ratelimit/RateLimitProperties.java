package com.mohan.walletflow.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token bucket per user: up to {@code capacity} requests in a burst, refilled at
 * {@code refillTokens} per {@code refillPeriod}.
 *
 * @param store {@code redis} (shared across instances) or {@code memory} (single instance / tests)
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, String store, long capacity, long refillTokens,
        Duration refillPeriod) {

    public double refillPerMilli() {
        return (double) refillTokens / refillPeriod.toMillis();
    }

    /** Time for an empty bucket to fill completely; used as the Redis key TTL. */
    public long fullRefillMillis() {
        return (long) Math.ceil(capacity / refillPerMilli());
    }
}
