package com.pwb.backend.modules.iam.service;

import java.time.Duration;

public interface PasswordResetTokenService {

    String issueToken(String email);

    String consumeToken(String token);

    void invalidate(String token);

    Duration ttl();
}
