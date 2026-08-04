package com.pwb.liveroom.infrastructure.persistence.entity;

import com.pwb.liveroom.domain.enums.MicState;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.enums.ParticipantState;
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
        name = "liveroom_participants",
        indexes = {
                @Index(name = "ix_liveroom_participants_room", columnList = "room_id"),
                @Index(name = "ix_liveroom_participants_user", columnList = "user_id"),
                @Index(name = "ix_liveroom_participants_cycle_state", columnList = "cycle_id, state, joined_at")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipantJpaEntity extends LiveroomJpaBaseEntity {

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "cycle_id", nullable = false, updatable = false)
    private UUID cycleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, updatable = false, length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_role", nullable = false, updatable = false, length = 16)
    private ParticipantRole roomRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private ParticipantState state;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "camera_on", nullable = false)
    private boolean cameraOn;

    @Column(name = "mic_on", nullable = false)
    private boolean micOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "mic_state", nullable = false, length = 20)
    private MicState micState;

    @Column(name = "mic_muted_by_owner_at")
    private Instant micMutedByOwnerAt;

    @Column(name = "mic_unmute_cooldown_until")
    private Instant micUnmuteCooldownUntil;

    @Column(name = "last_interaction_at")
    private Instant lastInteractionAt;
}