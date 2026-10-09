package com.mohan.walletflow.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties properties;
    private final ObjectProvider<RateLimiter> limiter;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public RateLimitConfig(RateLimitProperties properties, ObjectProvider<RateLimiter> limiter,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.properties = properties;
        this.limiter = limiter;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    RateLimiter rateLimiter(ObjectProvider<StringRedisTemplate> redis) {
        if ("memory".equalsIgnoreCase(properties.store())) {
            return new InMemoryTokenBucketRateLimiter(properties);
        }
        return new RedisTokenBucketRateLimiter(redis.getObject(), properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.enabled()) {
            registry.addInterceptor(new RateLimitInterceptor(limiter.getObject(), properties, "transfers",
                    meterRegistry.getObject()))
                    .addPathPatterns("/api/v1/transfers", "/api/v1/transfers/**");
        }
    }
}
