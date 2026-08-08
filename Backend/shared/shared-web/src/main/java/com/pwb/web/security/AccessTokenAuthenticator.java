package com.pwb.web.security;

import java.util.Optional;


public interface AccessTokenAuthenticator {

    String BEARER_PREFIX = "Bearer ";


    Optional<AuthenticatedUser> authenticate(String accessToken);


    static Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}