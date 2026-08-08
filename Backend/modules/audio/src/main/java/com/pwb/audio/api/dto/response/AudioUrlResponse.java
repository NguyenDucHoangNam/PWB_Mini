package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.AudioUrlView;

import java.net.URL;
import java.time.Instant;

public record AudioUrlResponse(
        URL url,
        Instant expiresAt
) {

    public static AudioUrlResponse from(AudioUrlView view) {
        return new AudioUrlResponse(view.url(), view.expiresAt());
    }
}
