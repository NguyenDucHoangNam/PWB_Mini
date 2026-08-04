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
        name = "liveroom_room_members",
        indexes = @Index(name = "ix_liveroom_room_members_user", columnList = "user_id")
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMemberJpaEntity extends LiveroomJpaBaseEntity {

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "was_approved", nullable = false)
    private boolean wasApproved;

    @Column(name = "kicked_at")
    private Instant kickedAt;

    @Column(name = "kicked_cooldown_until")
    private Instant kickedCooldownUntil;

    @Column(name = "reject_count_by_owner", nullable = false)
    private int rejectCountByOwner;

    @Column(name = "reject_count_by_capacity", nullable = false)
    private int rejectCountByCapacity;
}