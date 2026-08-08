package com.pwb.iam.application.command;

public record ResetPasswordCommand(String token, String newPassword, String userAgent, String clientIp, String locale) {

    public ResetPasswordCommand {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
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

    public ResetPasswordCommand(String token, String newPassword, String userAgent, String clientIp) {
        this(token, newPassword, userAgent, clientIp, "vi");
    }

    public ResetPasswordCommand(String token, String newPassword, String userAgent) {
        this(token, newPassword, userAgent, "unknown", "vi");
    }
}
