package com.pwb.backend.modules.iam.session;

import java.time.Instant;

public record SessionMetadata(
        String refreshToken,
        String ip,
        String device,
        String location,
        Instant createdAt,
        String accessSignature) {
}
