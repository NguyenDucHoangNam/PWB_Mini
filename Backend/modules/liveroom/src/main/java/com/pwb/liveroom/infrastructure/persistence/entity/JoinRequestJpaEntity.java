package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.liveroom.domain.enums.RejectionReason;
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
        name = "liveroom_join_requests",
        indexes = {
                @Index(name = "ix_liveroom_join_requests_room_state", columnList = "room_id, state, created_at"),
                @Index(name = "ix_liveroom_join_requests_user", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinRequestJpaEntity extends LiveroomJpaBaseEntity {

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "cycle_id", nullable = false, updatable = false)
    private UUID cycleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, updatable = false, length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private JoinRequestState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 32)
    private RejectionReason rejectionReason;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;
}