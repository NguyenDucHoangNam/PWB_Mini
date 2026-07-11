package com.pwb.backend.modules.iam.session;

import java.util.UUID;

public record RotationResult(
        RotationStatus status,
        UUID userId,
        String newRefreshToken,
        boolean cookieUpdateRequired) {
}
