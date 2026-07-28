package com.pwb.iam.application.command;

public record LoginCommand(
        String usernameOrEmail,
        String password
) {

    public LoginCommand {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("usernameOrEmail must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }
}
