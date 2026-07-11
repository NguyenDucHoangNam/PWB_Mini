package com.pwb.backend.auth;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        String role) {
}