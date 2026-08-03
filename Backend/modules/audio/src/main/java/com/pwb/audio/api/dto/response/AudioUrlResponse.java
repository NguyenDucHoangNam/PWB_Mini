package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.domain.enums.AudioVariant;

import java.net.URL;
import java.time.Instant;

/**
 * @param variant the rendition actually served — it can differ from the one requested, and is
 *                {@code null} for assets with a single rendition such as voice tags
 */
public record AudioUrlResponse(
        URL url,
        Instant expiresAt,
        AudioVariant variant
) {

    public static AudioUrlResponse from(AudioUrlView view) {
        return new AudioUrlResponse(view.url(), view.expiresAt(), view.variant());
    }
}
