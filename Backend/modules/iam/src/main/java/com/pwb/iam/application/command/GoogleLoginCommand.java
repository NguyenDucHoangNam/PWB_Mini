package com.pwb.iam.application.command;

public record GoogleLoginCommand(String idToken, String clientIp, String userAgent, String locale) {

    public GoogleLoginCommand {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken must not be blank");
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
        if (locale == null || locale.isBlank()) {
            locale = "vi";
        }
    }

    public GoogleLoginCommand(String idToken, String clientIp, String userAgent) {
        this(idToken, clientIp, userAgent, null);
    }
}
