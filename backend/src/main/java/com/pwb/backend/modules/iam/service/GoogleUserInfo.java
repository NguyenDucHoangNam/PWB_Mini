package com.pwb.backend.modules.iam.service;

public record GoogleUserInfo(
        String email,
        String fullName,
        String avatarUrl,
        String googleSubId) {
}
