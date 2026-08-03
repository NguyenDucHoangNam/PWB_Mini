package com.pwb.iam.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pwb.iam.application.facade.AuthView;

import java.util.UUID;

/**
 * Authentication result returned to the client.
 * <p>
 * The refresh token is intentionally absent: it is delivered only as an HttpOnly cookie. Echoing
 * it here as well would put it within reach of any script on the page, which is precisely what
 * the HttpOnly flag exists to prevent. Clients must send credentialed requests so the browser
 * attaches the cookie on {@code POST /api/v1/auth/refresh}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role,
        String tokenType,
        long expiresIn,
        String accessToken
) {

    private static final String BEARER = "Bearer";

    public static AuthResponse from(AuthView view) {
        return new AuthResponse(
                view.userId(),
                view.email(),
                view.fullName(),
                view.avatarUrl(),
                view.status(),
                view.role(),
                BEARER,
                view.expiresInSeconds(),
                view.accessToken()
        );
    }
}
