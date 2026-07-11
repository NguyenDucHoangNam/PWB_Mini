package com.pwb.backend.audio.api.dto.response;

import java.time.Instant;

public record WsTokenResponse(
    String token,
    Instant expiresAt,
    String destinationPrefix
) {}