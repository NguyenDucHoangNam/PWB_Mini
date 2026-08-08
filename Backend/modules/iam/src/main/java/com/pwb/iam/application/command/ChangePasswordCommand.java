package com.pwb.iam.application.command;

import java.util.UUID;

public record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword, String userAgent, String clientIp, String locale) {

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
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
        if (locale == null || locale.isBlank()) {
            locale = "vi";
        }
    }

    public ChangePasswordCommand(UUID userId, String currentPassword, String newPassword,
                                 String userAgent, String clientIp) {
        this(userId, currentPassword, newPassword, userAgent, clientIp, "vi");
    }
}