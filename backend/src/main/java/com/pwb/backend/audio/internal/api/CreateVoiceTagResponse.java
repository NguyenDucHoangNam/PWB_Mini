package com.pwb.backend.audio.internal.api;

import java.time.Instant;

public record CreateVoiceTagResponse(
    String id,
    String textContent,
    String languageCode,
    String voiceName,
    boolean isDefault,
    Instant createdAt
) {}
