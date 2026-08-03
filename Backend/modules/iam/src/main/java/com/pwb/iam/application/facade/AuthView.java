package com.pwb.iam.application.facade;

import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.domain.model.User;

import java.util.UUID;

/**
 * What the API layer needs after a successful authentication. Built from a {@link LoginResult}
 * so the mapping lives in one place rather than being repeated per use case.
 */
public record AuthView(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {

    public static AuthView from(LoginResult result) {
        User user = result.user();
        return new AuthView(
                user.getUserId(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getRole() == null ? null : user.getRole().name(),
                result.accessToken().tokenValue(),
                result.refreshToken().rawToken(),
                result.accessToken().expiresInSeconds()
        );
    }
}
