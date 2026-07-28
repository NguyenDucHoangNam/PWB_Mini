package com.pwb.iam.application.command;

public record LoginCommand(
        String email,
        String rawPassword,
        String clientIp
) {

    public LoginCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
    }
}