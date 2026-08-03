package com.pwb.iam.application.facade;

import java.util.UUID;

public record ProfileView(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role
) {

    public static ProfileView from(
            UUID userId,
            String email,
            String fullName,
            String avatarUrl,
            String status,
            String role
    ) {
        return new ProfileView(
                userId,
                email,
                fullName,
                avatarUrl,
                status,
                role
        );
    }
}
