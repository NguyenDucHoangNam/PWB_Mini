package com.pwb.backend.audio.api.event;

public record AudioProcessingEvent(
    String demoId,
    String s3Key,
    String userId,
    Integer watermarkInterval,
    String voiceTagId
) {}
