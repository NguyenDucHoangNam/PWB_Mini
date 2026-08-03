package com.pwb.iam.application.dto;

import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.domain.model.User;

import java.util.UUID;
import java.util.function.UnaryOperator;

public record AuthView(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role,
        String oauthProvider,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {

    public static AuthView from(LoginResult result, UnaryOperator<String> avatarUrlResolver) {
        User user = result.user();
        return new AuthView(
                user.getUserId(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getFullName(),
                avatarUrlResolver.apply(user.getAvatarUrl()),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getOauthProvider() == null ? null : user.getOauthProvider().name(),
                result.accessToken().tokenValue(),
                result.refreshToken().rawToken(),
                result.accessToken().expiresInSeconds()
        );
    }
}
