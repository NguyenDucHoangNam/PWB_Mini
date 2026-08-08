package com.pwb.iam.application.command;

public record ForgotPasswordCommand(String email, String userAgent, String locale, String clientIp) {

    public ForgotPasswordCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (locale == null || locale.isBlank()) {
            locale = "vi";
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
    }

    public ForgotPasswordCommand(String email, String userAgent) {
        this(email, userAgent, "vi", "unknown");
    }

    public ForgotPasswordCommand(String email, String userAgent, String locale) {
        this(email, userAgent, locale, "unknown");
    }
}