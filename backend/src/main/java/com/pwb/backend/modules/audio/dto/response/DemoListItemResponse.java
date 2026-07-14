package com.pwb.backend.modules.audio.dto.response;

import com.pwb.backend.modules.audio.enums.DemoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DemoListItemResponse(
        UUID demoId,
        String title,
        DemoStatus status,
        long fileSize,
        BigDecimal duration,
        Integer sampleRate,
        String format,
        UUID voiceTagId,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage) {
}
