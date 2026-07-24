package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.api.enums.AudioSource;
import com.pwb.liveroom.api.enums.LoopMode;
import com.pwb.liveroom.api.enums.PlaybackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "live_room_playback",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_live_room_playback_room_code", columnNames = "room_code")
    },
    indexes = {
        @Index(name = "ix_live_room_playback_deleted", columnList = "deleted"),
        @Index(name = "ix_live_room_playback_status", columnList = "status"),
        @Index(name = "ix_live_room_playback_song_id", columnList = "song_id")
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoomPlaybackJpaEntity extends LiveRoomBaseEntity {

    @Column(name = "room_code", nullable = false, length = 6, updatable = false)
    private String roomCode;

    @Column(name = "song_id")
    private UUID songId;

    @Column(name = "song_owner_user_id")
    private UUID songOwnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlaybackStatus status;

    @Column(name = "position_seconds", nullable = false)
    private long positionSeconds;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "playback_version", nullable = false)
    private long playbackVersion;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "playback_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal playbackRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "loop_mode", nullable = false, length = 16)
    private LoopMode loopMode;

    @Column(name = "shuffle_enabled", nullable = false)
    private boolean shuffleEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_source", length = 16)
    private AudioSource audioSource;
}
