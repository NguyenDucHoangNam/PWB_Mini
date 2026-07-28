package com.pwb.iam.infrastructure.security.service;

import com.pwb.iam.domain.service.RefreshTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenManagerAdapter implements RefreshTokenManager {

    private final RefreshTokenStore refreshTokenStore;

    @Override
    public void store(String jti, UUID userId, long ttlSeconds) {
        refreshTokenStore.store(jti, userId, ttlSeconds);
    }

    @Override
    public Optional<UUID> findUserIdByJti(String jti) {
        return refreshTokenStore.findUserId(jti);
    }

    @Override
    public void revoke(String jti) {
        refreshTokenStore.revoke(jti);
    }

    @Override
    public void revokeAllForUser(UUID userId) {
        refreshTokenStore.revokeAllForUser(userId);
    }
}
