package com.pwb.liveroom.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
    name = "live_room_participants",
    indexes = {
        @Index(name = "ix_participants_room_history", columnList = "room_code, joined_at"),
        @Index(name = "ix_participants_room_active_idx", columnList = "room_code"),
        @Index(name = "ix_participants_user_idx", columnList = "user_id")
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoomParticipantJpaEntity extends LiveRoomBaseEntity {

    @Column(name = "room_code", nullable = false, length = 6, updatable = false)
    private String roomCode;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "role_at_join", nullable = false, length = 32, updatable = false)
    private String roleAtJoin;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "mic_muted", nullable = false)
    private Boolean micMuted;

    @Column(name = "camera_off", nullable = false)
    private Boolean cameraOff;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}