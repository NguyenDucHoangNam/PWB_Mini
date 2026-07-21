package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.core.model.JoinRequestStatus;
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
    name = "live_room_join_requests",
    indexes = {
        @Index(name = "ix_join_requests_room_status", columnList = "room_code, status"),
        @Index(name = "ix_join_requests_user", columnList = "user_id"),
        @Index(name = "ix_join_requests_decided_at", columnList = "decided_at DESC")
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoomJoinRequestJpaEntity extends LiveRoomBaseEntity {

    @Column(name = "room_code", nullable = false, length = 6, updatable = false)
    private String roomCode;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "message", length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JoinRequestStatus status;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
