package com.pwb.backend.common.security.jwt;

import org.springframework.stereotype.Component;

@Component
public class BearerTokenExtractor {

    private final JwtProperties jwtProperties;

    public BearerTokenExtractor(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String extract(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String prefix = jwtProperties.getHeaderPrefix();
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    public String extractOrThrow(String authorizationHeader) {
        String token = extract(authorizationHeader);
        if (token == null) {
            throw new com.pwb.backend.common.exception.BusinessException(
                    com.pwb.backend.common.exception.CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
