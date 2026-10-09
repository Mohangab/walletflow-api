package com.mohan.walletflow.ratelimit;

public interface RateLimiter {

    /** Attempts to take one token from the bucket identified by {@code key}. */
    Decision tryConsume(String key);

    record Decision(boolean allowed, long remaining, long retryAfterMillis) {
    }
}
