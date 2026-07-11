package com.pwb.backend.modules.iam.service;

import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;

import java.util.UUID;

public interface SessionService {

    IssuedSession grantInitialSession(UUID userId);

    RotationResult rotate(String oldRefreshToken, UUID userIdFromExpiredJwt);

    boolean isActive(String refreshToken);

    String findUserIdForRefreshToken(String refreshToken);

    void revokeAllSessions(UUID userId);

    int revokeAllSessionsExcept(UUID userId, String currentRefreshToken);

    int revokeAllSessionsCompletely(UUID userId);

    void revokeSingleSession(String refreshToken);

    void blacklistAccessToken(String jwtSignature, long ttlSeconds);

    boolean isAccessTokenBlacklisted(String jwtSignature);
}
