package com.mohan.walletflow.common.error;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;
    private final long limit;

    public RateLimitExceededException(long retryAfterSeconds, long limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited",
                "Too many transfer requests; retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Map<String, String> getHeaders() {
        return Map.of(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds),
                "X-RateLimit-Limit", String.valueOf(limit),
                "X-RateLimit-Remaining", "0");
    }
}
