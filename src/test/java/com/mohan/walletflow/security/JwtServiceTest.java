package com.mohan.walletflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.mohan.walletflow.user.Role;
import com.mohan.walletflow.user.User;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef-test".getBytes());

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final JwtService jwt = new JwtService(
            new JwtProperties(SECRET, "walletflow-test", Duration.ofMinutes(15), Duration.ofDays(7)), clock);

    @Test
    void issuedTokenRoundTripsToPrincipal() {
        User user = new User("a@b.c", "hash", "A", Role.ADMIN);

        AuthenticatedUser principal = jwt.verifyAccessToken(jwt.issueAccessToken(user)).orElseThrow();

        assertThat(principal.id()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo("a@b.c");
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwt.issueAccessToken(new User("a@b.c", "hash", "A", Role.USER));
        clock.advance(Duration.ofMinutes(16));

        assertThat(jwt.verifyAccessToken(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString("another-secret-another-secret-another!".getBytes());
        JwtService other = new JwtService(
                new JwtProperties(otherSecret, "walletflow-test", Duration.ofMinutes(15), Duration.ofDays(7)), clock);
        String forged = other.issueAccessToken(new User("a@b.c", "hash", "A", Role.ADMIN));

        assertThat(jwt.verifyAccessToken(forged)).isEmpty();
        assertThat(jwt.verifyAccessToken("not.a.jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> new JwtService(
                new JwtProperties(shortSecret, "x", Duration.ofMinutes(1), Duration.ofDays(1)), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
