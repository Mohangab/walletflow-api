package com.mohan.walletflow.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import com.mohan.walletflow.common.error.RateLimitExceededException;
import com.mohan.walletflow.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-user throttling of transfer requests. Runs after authentication so the bucket key is the user id.
 * If Redis is unreachable the limiter fails open (requests proceed, an error is logged) so a cache outage
 * does not take payments down; idempotency and balance checks still protect correctness.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final String bucketName;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties, String bucketName) {
        this.limiter = limiter;
        this.properties = properties;
        this.bucketName = bucketName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return true;
        }
        RateLimiter.Decision decision;
        try {
            decision = limiter.tryConsume("rate:" + bucketName + ":" + user.id());
        } catch (RuntimeException ex) {
            log.error("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            onLimiterError();
            return true;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.capacity()));
        if (!decision.allowed()) {
            onRejected();
            throw new RateLimitExceededException(Math.max(1, (decision.retryAfterMillis() + 999) / 1000),
                    properties.capacity());
        }
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        return true;
    }

    /** Extension points for metrics. */
    protected void onRejected() {
    }

    protected void onLimiterError() {
    }
}
