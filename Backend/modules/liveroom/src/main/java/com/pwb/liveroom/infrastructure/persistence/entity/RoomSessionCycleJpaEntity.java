package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.domain.enums.EndedReason;
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
        name = "liveroom_session_cycles",
        indexes = @Index(name = "ix_liveroom_session_cycles_room_id", columnList = "room_id")
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomSessionCycleJpaEntity extends LiveroomJpaBaseEntity {

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "cycle_number", nullable = false, updatable = false)
    private int cycleNumber;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason", length = 32)
    private EndedReason endedReason;
}