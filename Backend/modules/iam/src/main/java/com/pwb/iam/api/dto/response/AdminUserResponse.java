package com.pwb.iam.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pwb.iam.application.dto.AdminUserView;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminUserResponse(
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

    public static AdminUserResponse from(AdminUserView view) {
        return new AdminUserResponse(
                view.userId(),
                view.email(),
                view.fullName(),
                view.avatarUrl(),
                view.status(),
                view.role(),
                view.oauthProvider(),
                view.banReason(),
                view.bannedAt(),
                view.bannedBy(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
