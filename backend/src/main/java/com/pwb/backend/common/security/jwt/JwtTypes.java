package com.pwb.backend.common.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public final class JwtTypes {

    private JwtTypes() {
    }

    public record AuthenticatedUser(
            UUID userId,
            String email,
            String role) {
    }

    public static final class JwtAuthenticationToken extends AbstractAuthenticationToken {

        private static final String ROLE_PREFIX = "ROLE_";

        private final AuthenticatedUser principal;

        public JwtAuthenticationToken(AuthenticatedUser principal) {
            super(List.of(new SimpleGrantedAuthority(ROLE_PREFIX + principal.role())));
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public AuthenticatedUser getPrincipal() {
            return principal;
        }
    }
}