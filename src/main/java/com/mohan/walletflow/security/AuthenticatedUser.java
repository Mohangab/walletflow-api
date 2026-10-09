package com.mohan.walletflow.security;

import java.util.UUID;

import com.mohan.walletflow.user.Role;

/** Principal built from a verified access token; no database lookup per request. */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
