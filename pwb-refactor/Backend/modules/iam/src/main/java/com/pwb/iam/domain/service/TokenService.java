package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.User;

public interface TokenService {

    AccessToken issueAccessToken(User user);

    long accessTokenExpiresInSeconds();

    record AccessToken(String tokenValue, String jti, java.time.Instant expiresAt, long expiresInSeconds) {
    }
}