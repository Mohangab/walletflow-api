package com.mohan.walletflow.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties properties;
    private final ObjectProvider<RateLimiter> limiter;

    public RateLimitConfig(RateLimitProperties properties, ObjectProvider<RateLimiter> limiter) {
        this.properties = properties;
        this.limiter = limiter;
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
            registry.addInterceptor(new RateLimitInterceptor(limiter.getObject(), properties, "transfers"))
                    .addPathPatterns("/api/v1/transfers", "/api/v1/transfers/**");
        }
    }
}
