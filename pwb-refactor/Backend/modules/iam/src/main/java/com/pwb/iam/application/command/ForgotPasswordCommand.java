package com.pwb.iam.application.command;

public record ForgotPasswordCommand(String email) {

    public ForgotPasswordCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }
}