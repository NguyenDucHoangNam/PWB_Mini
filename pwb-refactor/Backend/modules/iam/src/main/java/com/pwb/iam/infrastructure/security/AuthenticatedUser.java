package com.pwb.iam.infrastructure.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
public class AuthenticatedUser {

    private final UUID userId;
    private final String email;
    private final String role;

    public AuthenticatedUser(UUID userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }
}