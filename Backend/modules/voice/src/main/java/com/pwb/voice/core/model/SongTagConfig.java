package com.pwb.voice.core.model;

import lombok.Getter;

import java.util.UUID;

@Getter
public final class SongTagConfig {

    private final UUID id;
    private final UUID songId;
    private UUID voiceTagId;
    private Integer intervalSeconds;
    private Integer volumePercentage;
    private Integer fadeInDurationMs;
    private Integer fadeOutDurationMs;
    private Integer startOffsetSeconds;
    private boolean enabled;

    private SongTagConfig(
            UUID id,
            UUID songId,
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer fadeInDurationMs,
            Integer fadeOutDurationMs,
            Integer startOffsetSeconds,
            boolean enabled
    ) {
        this.id = id;
        this.songId = songId;
        this.voiceTagId = voiceTagId;
        this.intervalSeconds = intervalSeconds;
        this.volumePercentage = volumePercentage;
        this.fadeInDurationMs = fadeInDurationMs;
        this.fadeOutDurationMs = fadeOutDurationMs;
        this.startOffsetSeconds = startOffsetSeconds;
        this.enabled = enabled;
    }

    public static SongTagConfig create(
            UUID songId,
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer fadeInDurationMs,
            Integer fadeOutDurationMs,
            Integer startOffsetSeconds
    ) {
        return new SongTagConfig(
                UUID.randomUUID(),
                songId,
                voiceTagId,
                intervalSeconds,
                volumePercentage,
                fadeInDurationMs,
                fadeOutDurationMs,
                startOffsetSeconds == null ? 0 : startOffsetSeconds,
                true
        );
    }

    public static SongTagConfig rehydrate(
            UUID id,
            UUID songId,
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer fadeInDurationMs,
            Integer fadeOutDurationMs,
            Integer startOffsetSeconds,
            boolean enabled
    ) {
        return new SongTagConfig(
                id,
                songId,
                voiceTagId,
                intervalSeconds,
                volumePercentage,
                fadeInDurationMs,
                fadeOutDurationMs,
                startOffsetSeconds,
                enabled
        );
    }

    public void updateParams(
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer fadeInDurationMs,
            Integer fadeOutDurationMs,
            Integer startOffsetSeconds,
            Boolean enabled
    ) {
        if (voiceTagId != null) this.voiceTagId = voiceTagId;
        if (intervalSeconds != null) this.intervalSeconds = intervalSeconds;
        if (volumePercentage != null) this.volumePercentage = volumePercentage;
        if (fadeInDurationMs != null) this.fadeInDurationMs = fadeInDurationMs;
        if (fadeOutDurationMs != null) this.fadeOutDurationMs = fadeOutDurationMs;
        if (startOffsetSeconds != null) this.startOffsetSeconds = startOffsetSeconds;
        if (enabled != null) this.enabled = enabled;
    }
}
