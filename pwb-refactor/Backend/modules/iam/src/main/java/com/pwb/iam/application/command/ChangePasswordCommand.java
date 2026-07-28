package com.pwb.iam.application.command;

import java.util.UUID;

public record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {

    public ChangePasswordCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("currentPassword must not be blank");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("newPassword must not be blank");
        }
    }
}