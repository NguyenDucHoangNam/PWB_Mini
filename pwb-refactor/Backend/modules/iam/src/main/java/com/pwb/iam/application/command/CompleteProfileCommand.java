package com.pwb.iam.application.command;

public record CompleteProfileCommand(
        java.util.UUID userId,
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

    public CompleteProfileCommand(java.util.UUID userId, String username, String fullName) {
        this(userId, username, fullName, null);
    }
}
