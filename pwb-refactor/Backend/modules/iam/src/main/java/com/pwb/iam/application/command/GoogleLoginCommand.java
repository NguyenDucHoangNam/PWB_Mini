package com.pwb.iam.application.command;

public record GoogleLoginCommand(String idToken) {

    public GoogleLoginCommand {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken must not be blank");
        }
    }
}