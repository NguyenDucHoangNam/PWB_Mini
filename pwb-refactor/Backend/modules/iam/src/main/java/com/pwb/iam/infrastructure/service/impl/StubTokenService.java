package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class StubTokenService implements TokenService {

    private static final long EXPIRES_IN_SECONDS = 3600L;

    @Override
    public AccessToken issueAccessToken(User user) {
        UUID userId = user == null ? null : user.getUserId();
        String payload = userId == null ? "anonymous" : userId.toString();
        String jti = UUID.randomUUID().toString();
        log.info("Issued stub access token for userId={}", payload);
        return new AccessToken("stub-access-" + payload, jti, Instant.now().plusSeconds(EXPIRES_IN_SECONDS), EXPIRES_IN_SECONDS);
    }

    @Override
    public long accessTokenExpiresInSeconds() {
        return EXPIRES_IN_SECONDS;
    }
}