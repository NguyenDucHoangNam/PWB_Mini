package com.pwb.iam.application.command;

public record ForgotPasswordCommand(String email, String userAgent, String locale) {

    public ForgotPasswordCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    public ForgotPasswordCommand(String email, String userAgent) {
        this(email, userAgent, "vi");
    }
}
