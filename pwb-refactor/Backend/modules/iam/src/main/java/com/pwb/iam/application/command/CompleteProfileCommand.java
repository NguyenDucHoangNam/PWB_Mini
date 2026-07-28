package com.pwb.iam.application.command;

public record CompleteProfileCommand(
        java.util.UUID userId,
        String username,
        String fullName
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
