package com.pwb.backend.modules.iam.service;

import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.SessionMetadata;

import java.util.List;
import java.util.UUID;

public interface SessionService {

    IssuedSession grantInitialSession(UUID userId);

    RotationResult rotate(String oldRefreshToken, UUID userIdFromExpiredJwt);

    boolean isActive(String refreshToken);

    String findUserIdForRefreshToken(String refreshToken);

    void revokeAllSessions(UUID userId);

    int revokeAllSessionsExcept(UUID userId, String currentRefreshToken);

    int revokeAllSessionsCompletely(UUID userId);

    void purgeUserSessionData(UUID userId);

    void revokeSingleSession(String refreshToken);

    void revokeSingleSessionForCurrent(UUID userId, String refreshToken, String currentRefreshToken, String currentAccessSignature);

    int revokeAllOtherSessions(UUID userId, String currentRefreshToken);

    List<SessionMetadata> listActiveSessions(UUID userId, String currentRefreshToken);

    void writeSessionMetadata(UUID userId, String refreshToken, String accessSignature, String ip, String device, String location);

    void updateSessionSignature(String refreshToken, String accessSignature);

    void blacklistAccessToken(String jwtSignature, long ttlSeconds);

    boolean isAccessTokenBlacklisted(String jwtSignature);
}
