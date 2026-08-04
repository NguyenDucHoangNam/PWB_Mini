package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;


public final class RoomSessionCycle extends DomainBaseEntity {

    private final UUID id;
    private final UUID roomId;
    private final int cycleNumber;
    private final Instant startedAt;
    private Instant endedAt;
    private EndedReason endedReason;

    private RoomSessionCycle(
            UUID id,
            UUID roomId,
            int cycleNumber,
            Instant startedAt,
            Instant endedAt,
            EndedReason endedReason
    ) {
        this.id = id;
        this.roomId = roomId;
        this.cycleNumber = cycleNumber;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endedReason = endedReason;
    }

    public static RoomSessionCycle start(UUID roomId, int cycleNumber, Instant startedAt) {
        if (roomId == null) {
            throw new IllegalArgumentException("roomId must not be null");
        }
        if (cycleNumber < 1) {
            throw new IllegalArgumentException("cycleNumber must start at 1");
        }
        return new RoomSessionCycle(null, roomId, cycleNumber, startedAt, null, null);
    }

    public static RoomSessionCycle rehydrate(
            UUID id,
            UUID roomId,
            int cycleNumber,
            Instant startedAt,
            Instant endedAt,
            EndedReason endedReason
    ) {
        return new RoomSessionCycle(id, roomId, cycleNumber, startedAt, endedAt, endedReason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public EndedReason getEndedReason() {
        return endedReason;
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public void close(EndedReason reason, Instant at) {
        this.endedAt = at;
        this.endedReason = reason;
        touch();
    }


    public void reopenSameCycle() {
        this.endedAt = null;
        this.endedReason = null;
        touch();
    }
}