package com.mohan.walletflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Exercises the Lua token bucket against a real Redis. Runs when Redis is reachable at
 * REDIS_HOST:REDIS_PORT (default localhost:6379), e.g. in CI via a service container; skipped otherwise.
 */
@EnabledIf("redisAvailable")
class RedisTokenBucketRateLimiterIT {

    private static final String HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(HOST, PORT));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @Test
    void enforcesCapacityAndReportsRetryAfter() {
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(redis,
                new RateLimitProperties(true, "redis", 3, 3, Duration.ofMinutes(1)));
        String key = "rate:test:" + UUID.randomUUID();

        assertThat(limiter.tryConsume(key).remaining()).isEqualTo(2);
        assertThat(limiter.tryConsume(key).allowed()).isTrue();
        assertThat(limiter.tryConsume(key).allowed()).isTrue();
        RateLimiter.Decision rejected = limiter.tryConsume(key);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMillis()).isBetween(1L, 20_000L);
        assertThat(redis.getExpire(key)).isPositive();
    }

    @Test
    void refillsAfterThePeriod() throws InterruptedException {
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(redis,
                new RateLimitProperties(true, "redis", 1, 1, Duration.ofMillis(300)));
        String key = "rate:test:" + UUID.randomUUID();

        assertThat(limiter.tryConsume(key).allowed()).isTrue();
        assertThat(limiter.tryConsume(key).allowed()).isFalse();
        Thread.sleep(400);
        assertThat(limiter.tryConsume(key).allowed()).isTrue();
    }
}
