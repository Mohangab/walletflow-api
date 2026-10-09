package com.mohan.walletflow.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Base class for domain errors that map to an RFC 9457 problem+json response.
 * {@code code} becomes the last segment of the problem {@code type} URI and a stable
 * machine-readable {@code code} property, so clients never have to parse messages.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** Extra response headers (e.g. {@code Retry-After}) to send with the problem. */
    public Map<String, String> getHeaders() {
        return Map.of();
    }
}
