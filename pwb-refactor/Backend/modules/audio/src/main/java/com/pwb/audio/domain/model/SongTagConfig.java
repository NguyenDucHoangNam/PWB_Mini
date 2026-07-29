package com.pwb.audio.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

public final class SongTagConfig extends DomainBaseEntity {

    private final UUID id;
    private final UUID songId;
    private final UUID voiceTagId;
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
            Integer startOffsetSeconds,
            boolean enabled
    ) {
        if (songId == null) {
            throw new IllegalArgumentException("songId must not be null");
        }
        if (voiceTagId == null) {
            throw new IllegalArgumentException("voiceTagId must not be null");
        }
        int interval = (intervalSeconds != null) ? intervalSeconds : 60;
        int volume = (volumePercentage != null) ? clampVolume(volumePercentage) : 100;
        int fadeIn = (fadeInDurationMs != null) ? fadeInDurationMs : 0;
        int fadeOut = (fadeOutDurationMs != null) ? fadeOutDurationMs : 0;
        int offset = (startOffsetSeconds != null) ? startOffsetSeconds : 0;

        return new SongTagConfig(
                UUID.randomUUID(),
                songId,
                voiceTagId,
                interval,
                volume,
                fadeIn,
                fadeOut,
                offset,
                enabled
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

    public UUID getId() {
        return id;
    }

    public UUID getSongId() {
        return songId;
    }

    public UUID getVoiceTagId() {
        return voiceTagId;
    }

    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }

    public Integer getVolumePercentage() {
        return volumePercentage;
    }

    public Integer getFadeInDurationMs() {
        return fadeInDurationMs;
    }

    public Integer getFadeOutDurationMs() {
        return fadeOutDurationMs;
    }

    public Integer getStartOffsetSeconds() {
        return startOffsetSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateParams(
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer fadeInDurationMs,
            Integer fadeOutDurationMs,
            Integer startOffsetSeconds,
            Boolean enabled
    ) {
        if (intervalSeconds != null && intervalSeconds > 0) {
            this.intervalSeconds = intervalSeconds;
        }
        if (volumePercentage != null) {
            this.volumePercentage = clampVolume(volumePercentage);
        }
        if (fadeInDurationMs != null && fadeInDurationMs >= 0) {
            this.fadeInDurationMs = fadeInDurationMs;
        }
        if (fadeOutDurationMs != null && fadeOutDurationMs >= 0) {
            this.fadeOutDurationMs = fadeOutDurationMs;
        }
        if (startOffsetSeconds != null && startOffsetSeconds >= 0) {
            this.startOffsetSeconds = startOffsetSeconds;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        touch();
    }

    public void enable() {
        this.enabled = true;
        touch();
    }

    public void disable() {
        this.enabled = false;
        touch();
    }

    private static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }
}
