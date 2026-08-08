package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Every field is optional. A client relying on the HttpOnly refresh cookie sends an empty body,
 * so {@code accessExpiresInSeconds} stays a nullable {@code Long} and callers must null-check it
 * before unboxing into a primitive.
 */
public record LogoutRequest(
        @Size(max = 2048) String refreshToken,
        @Size(max = 255) String accessJti,
        Long accessExpiresInSeconds
) {

    public static LogoutRequest empty() {
        return new LogoutRequest(null, null, null);
    }
}