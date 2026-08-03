package com.pwb.iam.application.command;

public record RegisterCommand(
        String email,
        String rawPassword,
        String fullName
) {

    public RegisterCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
    }
}
