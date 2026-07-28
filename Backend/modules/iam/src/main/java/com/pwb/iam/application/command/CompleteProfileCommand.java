package com.pwb.iam.application.command;

import java.util.UUID;

public record CompleteProfileCommand(
        UUID userId,
        String username,
        String fullName,
        String newPassword
) {
    public CompleteProfileCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }
}
