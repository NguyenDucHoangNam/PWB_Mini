package com.pwb.backend.modules.liveroom.entity;

import com.pwb.backend.modules.liveroom.enums.RoomMode;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Room {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "room_code", nullable = false, length = 6, updatable = false)
    private String roomCode;

    @Column(name = "host_id", nullable = false, updatable = false)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 15, updatable = false)
    private RoomMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private RoomStatus status;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public void markInactiveHost() {
        if (this.status == RoomStatus.ACTIVE) {
            this.status = RoomStatus.INACTIVE_HOST;
        }
    }

    public void markReactivated() {
        if (this.status == RoomStatus.INACTIVE_HOST) {
            this.status = RoomStatus.ACTIVE;
        }
    }

    public void markClosed(Instant when) {
        if (this.status != RoomStatus.CLOSED) {
            this.status = RoomStatus.CLOSED;
            this.closedAt = when;
        }
    }
}