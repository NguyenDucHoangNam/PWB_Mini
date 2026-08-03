package com.pwb.iam.application.facade;

import com.pwb.iam.domain.model.User;

import java.util.UUID;

public record ProfileView(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role
) {

    public static ProfileView from(User user) {
        return new ProfileView(
                user.getUserId(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getRole() == null ? null : user.getRole().name()
        );
    }
}
