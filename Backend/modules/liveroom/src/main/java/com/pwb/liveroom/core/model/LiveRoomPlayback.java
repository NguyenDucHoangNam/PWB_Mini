package com.pwb.liveroom.core.model;

import com.pwb.liveroom.api.enums.LoopMode;
import com.pwb.liveroom.api.enums.PlaybackStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class LiveRoomPlayback {

    private final UUID id;
    private final String roomCode;
    private final UUID songId;
    private final UUID songOwnerUserId;
    private final PlaybackStatus status;
    private final long positionSeconds;
    private final Instant effectiveAt;
    private final long playbackVersion;
    private final UUID changedByUserId;
    private final Instant changedAt;
    private final BigDecimal playbackRate;
    private final LoopMode loopMode;
    private final boolean shuffleEnabled;

    private LiveRoomPlayback(
            UUID id,
            String roomCode,
            UUID songId,
            UUID songOwnerUserId,
            PlaybackStatus status,
            long positionSeconds,
            Instant effectiveAt,
            long playbackVersion,
            UUID changedByUserId,
            Instant changedAt,
            BigDecimal playbackRate,
            LoopMode loopMode,
            boolean shuffleEnabled
    ) {
        this.id = id;
        this.roomCode = roomCode;
        this.songId = songId;
        this.songOwnerUserId = songOwnerUserId;
        this.status = status;
        this.positionSeconds = positionSeconds;
        this.effectiveAt = effectiveAt;
        this.playbackVersion = playbackVersion;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
        this.playbackRate = playbackRate;
        this.loopMode = loopMode;
        this.shuffleEnabled = shuffleEnabled;
    }

    public static LiveRoomPlayback rehydrate(
            UUID id,
            String roomCode,
            UUID songId,
            UUID songOwnerUserId,
            PlaybackStatus status,
            long positionSeconds,
            Instant effectiveAt,
            long playbackVersion,
            UUID changedByUserId,
            Instant changedAt,
            BigDecimal playbackRate,
            LoopMode loopMode,
            boolean shuffleEnabled
    ) {
        validateRoomCode(roomCode);
        validateStatus(status);
        validatePosition(positionSeconds);
        validatePlaybackVersion(playbackVersion);
        if (effectiveAt == null) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_EFFECTIVE_AT_REQUIRED", "effectiveAt is required");
        }
        if (changedAt == null) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_CHANGED_AT_REQUIRED", "changedAt is required");
        }
        if (playbackRate == null) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_RATE_REQUIRED", "playbackRate is required");
        }
        if (loopMode == null) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_LOOP_REQUIRED", "loopMode is required");
        }
        return new LiveRoomPlayback(
                id,
                roomCode,
                songId,
                songOwnerUserId,
                status,
                positionSeconds,
                effectiveAt,
                playbackVersion,
                changedByUserId,
                changedAt,
                playbackRate,
                loopMode,
                shuffleEnabled
        );
    }

    public boolean isEmpty() {
        return songId == null;
    }

    private static void validateRoomCode(String roomCode) {
        if (roomCode == null || roomCode.length() != 6) {
            throw new LiveroomDomainException("LIVEROOM_CODE_INVALID_LENGTH", "Room code must be exactly 6 characters");
        }
    }

    private static void validateStatus(PlaybackStatus status) {
        if (status == null) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_STATUS_REQUIRED", "Playback status is required");
        }
    }

    private static void validatePosition(long positionSeconds) {
        if (positionSeconds < 0) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_POSITION_INVALID", "Position seconds must be non-negative");
        }
    }

    private static void validatePlaybackVersion(long playbackVersion) {
        if (playbackVersion < 0) {
            throw new LiveroomDomainException("LIVEROOM_PLAYBACK_VERSION_INVALID", "Playback version must be non-negative");
        }
    }
}
