package com.pwb.iam.api.dto;

public record GoogleIdTokenPayload(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String picture) {
}