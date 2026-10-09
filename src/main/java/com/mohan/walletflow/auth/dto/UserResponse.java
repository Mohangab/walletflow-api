package com.mohan.walletflow.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.mohan.walletflow.user.Role;
import com.mohan.walletflow.user.User;

public record UserResponse(UUID id, String email, String fullName, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }
}
