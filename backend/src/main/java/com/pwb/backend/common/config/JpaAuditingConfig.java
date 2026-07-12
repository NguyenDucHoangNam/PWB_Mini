package com.pwb.backend.common.config;

import com.pwb.backend.common.model.BaseEntity;
import com.pwb.backend.common.security.jwt.JwtTypes.AuthenticatedUser;
import com.pwb.backend.common.security.jwt.JwtTypes.JwtAuthenticationToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth && jwtAuth.isAuthenticated()) {
                AuthenticatedUser principal = jwtAuth.getPrincipal();
                if (principal != null && principal.userId() != null) {
                    return Optional.of(principal.userId().toString());
                }
            }
            return Optional.of(BaseEntity.SYSTEM_PRINCIPAL);
        };
    }
}
