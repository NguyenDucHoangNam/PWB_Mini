package com.pwb.iam.application.command;

public record ResetPasswordCommand(
        String token,
        String newPassword
) {
    public ResetPasswordCommand {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("newPassword must not be blank");
        }
    }
}
