package com.pwb.audio.application.view;

import com.pwb.audio.domain.enums.AudioVariant;

import java.net.URL;
import java.time.Instant;

public record AudioUrlView(
        URL url,
        Instant expiresAt,
        AudioVariant variant
) {

    public static AudioUrlView single(URL url, Instant expiresAt) {
        return new AudioUrlView(url, expiresAt, null);
    }
}
