package com.pwb.iam.infrastructure.security;

import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.service.impl.TokenManagerServiceAdapter;
import com.pwb.web.security.AccessTokenAuthenticator;
import com.pwb.web.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class JwtAccessTokenAuthenticatorAdapter implements AccessTokenAuthenticator {

    private static final String ROLE_PREFIX = "ROLE_";

    private final TokenManagerServiceAdapter tokenManager;
    private final TokenManagerService blacklistService;

    @Override
    public Optional<AuthenticatedUser> authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }

        TokenManagerService.ParseResult result = tokenManager.parseAccessTokenWithResult(accessToken);
        if (!result.valid()) {
            log.debug("JWT validation failed: error={}", result.error());
            return Optional.empty();
        }

        String jti = result.claims().getId();
        if (jti != null && blacklistService.isAccessTokenBlacklisted(jti)) {
            log.debug("Token is blacklisted: jti={}", jti);
            return Optional.empty();
        }

        String subject = result.claims().getSubject();
        if (subject == null) {
            return Optional.empty();
        }

        String role = result.claims().get("role", String.class);
        return Optional.of(AuthenticatedUser.builder()
                .userId(subject)
                .email(result.claims().get("email", String.class))
                .authorities(role == null ? Set.of() : Set.of(ROLE_PREFIX + role))
                .isOAuthUser(Boolean.TRUE.equals(result.claims().get("oauth", Boolean.class)))
                .build());
    }
}