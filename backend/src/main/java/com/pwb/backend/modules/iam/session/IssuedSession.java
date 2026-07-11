package com.pwb.backend.modules.iam.session;

import java.time.Instant;
import java.util.UUID;

public record IssuedSession(
        UUID userId,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String kickedRefreshToken) {
}
