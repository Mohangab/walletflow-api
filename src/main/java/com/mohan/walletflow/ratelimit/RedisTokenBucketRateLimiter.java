package com.mohan.walletflow.ratelimit;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Distributed token bucket: state lives in a Redis hash and is updated atomically by a Lua script,
 * so every application instance enforces one shared limit per user.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"),
            List.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Decision tryConsume(String key) {
        @SuppressWarnings("unchecked")
        List<Long> result = redis.execute(SCRIPT, List.of(key),
                String.valueOf(properties.capacity()),
                String.valueOf(properties.refillPerMilli()),
                String.valueOf(properties.fullRefillMillis() + 1000));
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Unexpected rate limiter script result: " + result);
        }
        return new Decision(result.get(0) == 1L, result.get(1), result.get(2));
    }
}
