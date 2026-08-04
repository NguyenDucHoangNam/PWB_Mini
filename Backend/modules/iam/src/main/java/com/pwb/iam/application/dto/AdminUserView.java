package com.pwb.iam.application.dto;

import com.pwb.iam.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public record AdminUserView(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role,
        String oauthProvider,
        String banReason,
        Instant bannedAt,
        UUID bannedBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminUserView from(User user, Instant createdAt, Instant updatedAt) {
        return new AdminUserView(
                user.getUserId(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getOauthProvider() == null ? null : user.getOauthProvider().name(),
                user.getBanReason(),
                user.getBannedAt(),
                user.getBannedBy(),
                createdAt,
                updatedAt
        );
    }
}