package com.pwb.backend.audio.api.dto.response;

public record RequestOtpResponse(
    int cooldownSeconds,
    int ttlSeconds
) {}