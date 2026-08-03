package com.pwb.audio.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

public final class SongTagConfig extends DomainBaseEntity {

    private static final int DEFAULT_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_VOLUME_PERCENTAGE = 100;
    private static final int NO_DUCKING = 100;

    private final UUID id;
    private final UUID songId;
    private final UUID voiceTagId;
    private Integer intervalSeconds;
    private Integer volumePercentage;
    private Integer duckingPercentage;
    private Integer startOffsetSeconds;
    private boolean enabled;

    private SongTagConfig(
            UUID id,
            UUID songId,
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer duckingPercentage,
            Integer startOffsetSeconds,
            boolean enabled
    ) {
        this.id = id;
        this.songId = songId;
        this.voiceTagId = voiceTagId;
        this.intervalSeconds = intervalSeconds;
        this.volumePercentage = volumePercentage;
        this.duckingPercentage = duckingPercentage;
        this.startOffsetSeconds = startOffsetSeconds;
        this.enabled = enabled;
    }

    public static SongTagConfig create(
            UUID songId,
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer duckingPercentage,
            Integer startOffsetSeconds,
            boolean enabled
    ) {
        if (songId == null) {
            throw new IllegalArgumentException("songId must not be null");
        }
        if (voiceTagId == null) {
            throw new IllegalArgumentException("voiceTagId must not be null");
        }
        return new SongTagConfig(
                null,
                songId,
                voiceTagId,
                (intervalSeconds != null) ? intervalSeconds : DEFAULT_INTERVAL_SECONDS,
                (volumePercentage != null) ? clampPercentage(volumePercentage) : DEFAULT_VOLUME_PERCENTAGE,
                (duckingPercentage != null) ? clampPercentage(duckingPercentage) : NO_DUCKING,
                (startOffsetSeconds != null) ? Math.max(0, startOffsetSeconds) : 0,
                enabled
        );
    }

    public static SongTagConfig rehydrate(
            UUID id,
            UUID songId,
            UUID voiceTagId,
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer duckingPercentage,
            Integer startOffsetSeconds,
            boolean enabled
    ) {
        return new SongTagConfig(
                id,
                songId,
                voiceTagId,
                intervalSeconds,
                volumePercentage,
                duckingPercentage,
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

    public Integer getDuckingPercentage() {
        return duckingPercentage;
    }

    public Integer getStartOffsetSeconds() {
        return startOffsetSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static int clampPercentage(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
