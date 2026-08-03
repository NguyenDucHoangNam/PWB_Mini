package com.pwb.iam.application.command;

import java.util.UUID;

public record UpdateProfileCommand(
        UUID userId,
        String fullName
) {

    public UpdateProfileCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
    }
}
