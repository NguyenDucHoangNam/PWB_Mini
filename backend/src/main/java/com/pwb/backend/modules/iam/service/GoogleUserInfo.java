package com.pwb.backend.modules.iam.service;

import com.pwb.backend.common.security.url.AvatarUrlValidator;

public record GoogleUserInfo(
        String email,
        String fullName,
        String avatarUrl,
        String googleSubId) {

    public static GoogleUserInfo sanitized(String email, String fullName, String avatarUrl, String googleSubId) {
        return new GoogleUserInfo(
                email,
                fullName,
                AvatarUrlValidator.sanitizeGoogleAvatarUrl(avatarUrl),
                googleSubId);
    }
}
