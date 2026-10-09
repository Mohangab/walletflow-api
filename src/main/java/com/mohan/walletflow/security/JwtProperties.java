package com.mohan.walletflow.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret          Base64-encoded HMAC key (at least 256 bits)
 * @param issuer          value of the {@code iss} claim
 * @param accessTokenTtl  lifetime of the signed access token
 * @param refreshTokenTtl lifetime of the opaque refresh token
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
