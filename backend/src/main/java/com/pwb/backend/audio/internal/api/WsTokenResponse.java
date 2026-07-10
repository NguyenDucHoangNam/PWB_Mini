package com.pwb.backend.audio.internal.api;

import java.time.Instant;

public record WsTokenResponse(
    String token,
    Instant expiresAt,
    String destinationPrefix
) {}