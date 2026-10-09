package com.mohan.walletflow.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Single-node token bucket with the same semantics as the Redis script; used in tests and local dev. */
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private final RateLimitProperties properties;
    private final LongSupplier clockMillis;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryTokenBucketRateLimiter(RateLimitProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    InMemoryTokenBucketRateLimiter(RateLimitProperties properties, LongSupplier clockMillis) {
        this.properties = properties;
        this.clockMillis = clockMillis;
    }

    @Override
    public Decision tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(properties.capacity(), clockMillis.getAsLong()));
        synchronized (bucket) {
            long now = clockMillis.getAsLong();
            bucket.tokens = Math.min(properties.capacity(),
                    bucket.tokens + Math.max(0, now - bucket.timestamp) * properties.refillPerMilli());
            bucket.timestamp = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return new Decision(true, (long) Math.floor(bucket.tokens), 0);
            }
            long retry = (long) Math.ceil((1 - bucket.tokens) / properties.refillPerMilli());
            return new Decision(false, 0, retry);
        }
    }

    private static final class Bucket {
        private double tokens;
        private long timestamp;

        private Bucket(double tokens, long timestamp) {
            this.tokens = tokens;
            this.timestamp = timestamp;
        }
    }
}
