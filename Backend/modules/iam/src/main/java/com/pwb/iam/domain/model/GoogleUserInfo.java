package com.pwb.iam.domain.model;

public record GoogleUserInfo(
        String sub,
        String email,
        boolean verified,
        String name,
        String picture
) {
}
