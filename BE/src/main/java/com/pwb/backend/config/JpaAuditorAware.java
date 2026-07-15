package com.pwb.backend.config;

import org.slf4j.MDC;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaAuditorAware implements AuditorAware<String> {

    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String SYSTEM_ACTOR = "system";
    private static final String ASYNC_ACTOR_PREFIX = "async";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of(authentication.getName());
        }
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            return Optional.of(ASYNC_ACTOR_PREFIX + ":" + correlationId);
        }
        return Optional.of(SYSTEM_ACTOR);
    }
}
