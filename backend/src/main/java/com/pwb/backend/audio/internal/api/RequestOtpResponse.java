package com.pwb.backend.audio.internal.api;

public record RequestOtpResponse(
    int cooldownSeconds,
    int ttlSeconds
) {}