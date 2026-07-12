package com.pwb.backend.modules.audio.event;

import java.util.UUID;

public record AudioProcessingEvent(
        UUID demoId,
        String s3Key,
        UUID voiceTagId,
        Integer watermarkInterval,
        String requestId) {
}