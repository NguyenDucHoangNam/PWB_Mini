package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class StubTokenService implements TokenService {

    private static final long EXPIRES_IN_SECONDS = 3600L;

    @Override
    public String issueAccessToken(User user) {
        UUID userId = user == null ? null : user.getUserId();
        String payload = userId == null ? "anonymous" : userId.toString();
        log.info("Issued stub access token for userId={}", payload);
        return "stub-access-" + payload;
    }

    @Override
    public long accessTokenExpiresInSeconds() {
        return EXPIRES_IN_SECONDS;
    }

    public TokenResult issueFor(User user) {
        return new TokenResult(issueAccessToken(user), EXPIRES_IN_SECONDS, user.getStatus());
    }
}
