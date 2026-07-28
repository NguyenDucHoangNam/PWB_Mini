package com.pwb.iam.domain.service;

public interface AccessTokenBlacklist {

    void blacklist(String jti, long expiresInSeconds);

    boolean isBlacklisted(String jti);
}