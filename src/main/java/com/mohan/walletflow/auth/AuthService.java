package com.mohan.walletflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mohan.walletflow.auth.dto.LoginRequest;
import com.mohan.walletflow.auth.dto.RegisterRequest;
import com.mohan.walletflow.auth.dto.TokenResponse;
import com.mohan.walletflow.auth.dto.UserResponse;
import com.mohan.walletflow.common.error.ConflictException;
import com.mohan.walletflow.common.error.NotFoundException;
import com.mohan.walletflow.common.error.UnauthorizedException;
import com.mohan.walletflow.security.JwtProperties;
import com.mohan.walletflow.security.JwtService;
import com.mohan.walletflow.user.Role;
import com.mohan.walletflow.user.User;
import com.mohan.walletflow.user.UserRepository;

/**
 * Registration, login and refresh-token rotation.
 * <p>
 * Refresh tokens are single use: every refresh revokes the presented token and issues a new pair.
 * Presenting an already revoked token is treated as token theft and revokes every session of that user.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    // Used to keep login timing similar for unknown emails (avoids user enumeration).
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            JwtService jwtService, JwtProperties jwtProperties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("email-taken", "An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(),
                Role.USER));
        return UserResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new UnauthorizedException("invalid-credentials", "Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(String presentedToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(TokenHasher.sha256(presentedToken))
                .orElseThrow(() -> new UnauthorizedException("invalid-refresh-token", "Refresh token is invalid"));
        if (token.isRevoked()) {
            int revoked = refreshTokens.revokeAllForUser(token.getUserId());
            log.warn("Refresh token reuse detected for user {}; revoked {} active session(s)", token.getUserId(), revoked);
            throw new UnauthorizedException("refresh-token-reused",
                    "Refresh token was already used; all sessions have been revoked");
        }
        if (token.isExpired(now)) {
            throw new UnauthorizedException("refresh-token-expired", "Refresh token has expired");
        }
        token.revoke();
        User user = users.findById(token.getUserId()).orElseThrow(() -> new NotFoundException("User", token.getUserId()));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String presentedToken) {
        refreshTokens.findByTokenHashForUpdate(TokenHasher.sha256(presentedToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::from).orElseThrow(() -> new NotFoundException("User", userId));
    }

    private TokenResponse issueTokens(User user) {
        Instant now = clock.instant();
        String opaque = TokenHasher.newOpaqueToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenHasher.sha256(opaque), now,
                now.plus(jwtProperties.refreshTokenTtl())));
        return TokenResponse.bearer(jwtService.issueAccessToken(user), opaque, jwtService.accessTokenTtlSeconds());
    }
}
