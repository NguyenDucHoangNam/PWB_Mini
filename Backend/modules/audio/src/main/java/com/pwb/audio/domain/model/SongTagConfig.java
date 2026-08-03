package com.pwb.audio.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

/**
 * How a voice tag is stamped onto a song: every {@code intervalSeconds}, starting at
 * {@code startOffsetSeconds}, with the song itself dipped to {@code duckingPercentage} while the tag plays.
 */
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
        // id stays null until the row is persisted; that is what marks this instance as new.
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

    /** Volume the song keeps while a tag plays, as a percentage. 100 means no ducking at all. */
    public Integer getDuckingPercentage() {
        return duckingPercentage;
    }

    public Integer getStartOffsetSeconds() {
        return startOffsetSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isNew() {
        return id == null;
    }

    public void updateParams(
            Integer intervalSeconds,
            Integer volumePercentage,
            Integer duckingPercentage,
            Integer startOffsetSeconds,
            Boolean enabled
    ) {
        if (intervalSeconds != null && intervalSeconds > 0) {
            this.intervalSeconds = intervalSeconds;
        }
        if (volumePercentage != null) {
            this.volumePercentage = clampPercentage(volumePercentage);
        }
        if (duckingPercentage != null) {
            this.duckingPercentage = clampPercentage(duckingPercentage);
        }
        if (startOffsetSeconds != null && startOffsetSeconds >= 0) {
            this.startOffsetSeconds = startOffsetSeconds;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        touch();
    }

    private static int clampPercentage(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
