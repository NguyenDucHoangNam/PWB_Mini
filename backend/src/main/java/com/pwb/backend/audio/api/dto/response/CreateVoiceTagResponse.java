package com.pwb.backend.audio.api.dto.response;

import java.time.Instant;

public record CreateVoiceTagResponse(
    String id,
    String textContent,
    String languageCode,
    String voiceName,
    boolean isDefault,
    Instant createdAt
) {}
