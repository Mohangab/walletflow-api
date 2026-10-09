package com.mohan.walletflow.common.error;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Factory for consistently shaped problem+json bodies. */
public final class Problems {

    public static final String TYPE_PREFIX = "https://walletflow.dev/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
