package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.domain.enums.PlaybackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(
        name = "liveroom_playback_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_liveroom_playback_states_room", columnNames = "room_id")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaybackStateJpaEntity extends LiveroomJpaBaseEntity {

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "song_id")
    private UUID songId;

    @Column(name = "song_owner_id")
    private UUID songOwnerId;

    @Column(name = "song_title", length = 255)
    private String songTitle;

    @Column(name = "song_artist", length = 255)
    private String songArtist;

    @Column(name = "song_duration_seconds")
    private Integer songDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PlaybackStatus status;

    @Column(name = "position_seconds", nullable = false)
    private double positionSeconds;

    @Column(name = "volume_percent", nullable = false)
    private int volumePercent;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;
}