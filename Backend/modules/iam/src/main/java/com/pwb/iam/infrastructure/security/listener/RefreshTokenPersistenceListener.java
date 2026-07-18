package com.pwb.iam.infrastructure.security.listener;

import com.pwb.iam.core.events.AuthSuccessEvent;
import com.pwb.iam.infrastructure.security.config.RefreshTokenProperties;
import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import com.pwb.iam.infrastructure.security.service.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenPersistenceListener {

    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenProperties refreshTokenProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuthSuccess(AuthSuccessEvent event) {
        String jti = jwtTokenProvider.extractJtiFromRefreshToken(event.refreshToken());
        if (jti == null || jti.isBlank()) {
            log.warn("No JTI found in refresh token: userId={}", event.userId());
            return;
        }
        refreshTokenStore.store(jti, event.userId(), refreshTokenProperties.getTtlSeconds());
        log.debug("Refresh token stored after commit: userId={} jti={}", event.userId(), jti);
    }
}
