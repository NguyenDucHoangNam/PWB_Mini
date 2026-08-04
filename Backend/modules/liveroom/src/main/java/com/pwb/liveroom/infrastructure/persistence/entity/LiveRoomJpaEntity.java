package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.enums.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "liveroom_rooms",
        indexes = {
                @Index(name = "ix_liveroom_rooms_owner_id", columnList = "owner_id"),
                @Index(name = "ix_liveroom_rooms_status", columnList = "status"),
                @Index(name = "ix_liveroom_rooms_owner_created_at", columnList = "owner_id, created_at DESC")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoomJpaEntity extends LiveroomJpaBaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "room_code", nullable = false, updatable = false, length = 6)
    private String roomCode;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RoomStatus status;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "owner_grace_seconds", nullable = false)
    private int ownerGraceSeconds;

    @Column(name = "current_participant_count", nullable = false)
    private int currentParticipantCount;

    @Column(name = "reserved_owner_slot", nullable = false)
    private boolean reservedOwnerSlot;

    @Column(name = "owner_left_at")
    private Instant ownerLeftAt;

    @Column(name = "current_cycle_id")
    private UUID currentCycleId;

    @Column(name = "reopened_count", nullable = false)
    private int reopenedCount;

    @Column(name = "last_reopened_at")
    private Instant lastReopenedAt;

    @Column(name = "previous_ended_at")
    private Instant previousEndedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason", length = 32)
    private EndedReason endedReason;
}