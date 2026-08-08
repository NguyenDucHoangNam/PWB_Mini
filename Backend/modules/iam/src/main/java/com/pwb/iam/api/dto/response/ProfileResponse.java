package com.pwb.iam.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pwb.iam.application.dto.ProfileView;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        String status,
        String role,
        String oauthProvider
) {

    public static ProfileResponse from(ProfileView view) {
        return new ProfileResponse(
                view.userId(),
                view.email(),
                view.fullName(),
                view.avatarUrl(),
                view.status(),
                view.role(),
                view.oauthProvider()
        );
    }
}
