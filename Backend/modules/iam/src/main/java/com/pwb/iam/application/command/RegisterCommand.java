package com.pwb.iam.application.command;

import com.pwb.iam.domain.model.OAuthProvider;

public record RegisterCommand(
        String email,
        String rawPassword,
        String fullName,
        OAuthProvider provider
) {

    public RegisterCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
    }

    public static RegisterCommand local(String email, String rawPassword, String fullName) {
        return new RegisterCommand(email, rawPassword, fullName, OAuthProvider.LOCAL);
    }
}
