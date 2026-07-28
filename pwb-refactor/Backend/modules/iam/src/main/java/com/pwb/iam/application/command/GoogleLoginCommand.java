package com.pwb.iam.application.command;

public record GoogleLoginCommand(String idToken, String clientIp) {

    public GoogleLoginCommand {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken must not be blank");
        }
    }
}