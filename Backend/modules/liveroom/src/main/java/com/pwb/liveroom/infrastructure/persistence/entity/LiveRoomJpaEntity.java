package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.LiveRoomStatus;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "live_rooms",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_live_rooms_room_code", columnNames = "room_code")
    },
    indexes = {
        @Index(name = "ix_live_rooms_host_user_id", columnList = "host_user_id"),
        @Index(name = "ix_live_rooms_status", columnList = "status"),
        @Index(name = "ix_live_rooms_room_code", columnList = "room_code"),
        @Index(name = "ix_live_rooms_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoomJpaEntity extends LiveRoomBaseEntity {

    @Column(name = "host_user_id", nullable = false)
    private UUID hostUserId;

    @Column(name = "room_code", nullable = false, length = 6, updatable = false)
    private String roomCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 32)
    private LiveRoomMode mode;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LiveRoomStatus status;

    @Column(name = "current_participant_count", nullable = false)
    private int currentParticipantCount;

    @Column(name = "scheduled_start_at")
    private Instant scheduledStartAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}