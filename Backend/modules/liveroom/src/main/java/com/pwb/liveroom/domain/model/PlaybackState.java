package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.PlaybackStatus;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


public final class PlaybackState extends DomainBaseEntity {

    public static final int DEFAULT_VOLUME_PERCENT = 80;
    public static final int MIN_VOLUME_PERCENT = 0;
    public static final int MAX_VOLUME_PERCENT = 100;

    private final UUID id;
    private final UUID roomId;
    private UUID songId;
    private UUID songOwnerId;
    private String songTitle;
    private String songArtist;
    private Integer songDurationSeconds;
    private PlaybackStatus status;
    private double positionSeconds;
    private int volumePercent;
    private Instant startedAt;
    private Instant lastUpdatedAt;
    private UUID lastUpdatedBy;
    private long sequenceNumber;

    private PlaybackState(
            UUID id,
            UUID roomId,
            UUID songId,
            UUID songOwnerId,
            String songTitle,
            String songArtist,
            Integer songDurationSeconds,
            PlaybackStatus status,
            double positionSeconds,
            int volumePercent,
            Instant startedAt,
            Instant lastUpdatedAt,
            UUID lastUpdatedBy,
            long sequenceNumber
    ) {
        this.id = id;
        this.roomId = roomId;
        this.songId = songId;
        this.songOwnerId = songOwnerId;
        this.songTitle = songTitle;
        this.songArtist = songArtist;
        this.songDurationSeconds = songDurationSeconds;
        this.status = status;
        this.positionSeconds = positionSeconds;
        this.volumePercent = volumePercent;
        this.startedAt = startedAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.lastUpdatedBy = lastUpdatedBy;
        this.sequenceNumber = sequenceNumber;
    }


    public static PlaybackState silent(UUID roomId, Instant at) {
        if (roomId == null) {
            throw new IllegalArgumentException("roomId must not be null");
        }
        return new PlaybackState(null, roomId, null, null, null, null, null,
                PlaybackStatus.PAUSED, 0d, DEFAULT_VOLUME_PERCENT, null, at, null, 0L);
    }

    public static PlaybackState rehydrate(
            UUID id,
            UUID roomId,
            UUID songId,
            UUID songOwnerId,
            String songTitle,
            String songArtist,
            Integer songDurationSeconds,
            PlaybackStatus status,
            double positionSeconds,
            int volumePercent,
            Instant startedAt,
            Instant lastUpdatedAt,
            UUID lastUpdatedBy,
            long sequenceNumber
    ) {
        return new PlaybackState(id, roomId, songId, songOwnerId, songTitle, songArtist,
                songDurationSeconds, status, positionSeconds, volumePercent, startedAt,
                lastUpdatedAt, lastUpdatedBy, sequenceNumber);
    }


    public void selectSong(
            UUID songId,
            UUID songOwnerId,
            String songTitle,
            String songArtist,
            Integer songDurationSeconds,
            boolean startPlaying,
            UUID by,
            Instant at
    ) {
        if (songId == null || songOwnerId == null) {
            throw new IllegalArgumentException("songId and songOwnerId must not be null");
        }
        this.songId = songId;
        this.songOwnerId = songOwnerId;
        this.songTitle = songTitle;
        this.songArtist = songArtist;
        this.songDurationSeconds = songDurationSeconds;
        this.positionSeconds = 0d;
        this.status = startPlaying ? PlaybackStatus.PLAYING : PlaybackStatus.PAUSED;
        this.startedAt = startPlaying ? at : null;
        bump(by, at);
    }

    public void resume(UUID by, Instant at) {
        requireSong();

        this.status = PlaybackStatus.PLAYING;
        this.startedAt = at;
        bump(by, at);
    }


    public void pause(UUID by, Instant at) {
        requireSong();
        this.positionSeconds = positionAt(at);
        this.status = PlaybackStatus.PAUSED;
        this.startedAt = null;
        bump(by, at);
    }

    public void seekTo(double seconds, UUID by, Instant at) {
        requireSong();
        if (seconds < 0 || (songDurationSeconds != null && seconds > songDurationSeconds)) {
            throw new IllegalArgumentException("position out of range: " + seconds);
        }
        this.positionSeconds = seconds;

        this.startedAt = isPlaying() ? at : null;
        bump(by, at);
    }


    public void changeVolume(int percent, UUID by, Instant at) {
        if (percent < MIN_VOLUME_PERCENT || percent > MAX_VOLUME_PERCENT) {
            throw new IllegalArgumentException("volume out of range: " + percent);
        }
        this.volumePercent = percent;
        bump(by, at);
    }


    public double positionAt(Instant now) {
        double position = positionSeconds;
        if (status == PlaybackStatus.PLAYING && startedAt != null && now.isAfter(startedAt)) {
            position += Duration.between(startedAt, now).toMillis() / 1000d;
        }
        return songDurationSeconds == null ? position : Math.min(position, songDurationSeconds);
    }

    public boolean hasSong() {
        return songId != null;
    }

    public boolean isPlaying() {
        return status == PlaybackStatus.PLAYING;
    }

    public boolean isNew() {
        return id == null;
    }

    private void requireSong() {
        if (!hasSong()) {
            throw new IllegalStateException("No song is loaded");
        }
    }


    private void bump(UUID by, Instant at) {
        this.lastUpdatedBy = by;
        this.lastUpdatedAt = at;
        this.sequenceNumber += 1;
        touch();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getSongId() {
        return songId;
    }

    public UUID getSongOwnerId() {
        return songOwnerId;
    }

    public String getSongTitle() {
        return songTitle;
    }

    public String getSongArtist() {
        return songArtist;
    }

    public Integer getSongDurationSeconds() {
        return songDurationSeconds;
    }

    public PlaybackStatus getStatus() {
        return status;
    }


    public double getPositionSeconds() {
        return positionSeconds;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public UUID getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }
}