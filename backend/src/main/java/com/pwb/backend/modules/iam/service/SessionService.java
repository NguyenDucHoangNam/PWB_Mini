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

    void revokeSingleSession(String refreshToken);
}
