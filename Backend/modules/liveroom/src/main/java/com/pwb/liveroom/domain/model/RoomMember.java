package com.pwb.liveroom.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;


public final class RoomMember extends DomainBaseEntity {


    public static final int REJECT_LIMIT = 3;

    private final UUID id;
    private final UUID roomId;
    private final UUID userId;
    private boolean wasApproved;
    private Instant kickedAt;
    private Instant kickedCooldownUntil;
    private int rejectCountByOwner;
    private int rejectCountByCapacity;

    private RoomMember(
            UUID id,
            UUID roomId,
            UUID userId,
            boolean wasApproved,
            Instant kickedAt,
            Instant kickedCooldownUntil,
            int rejectCountByOwner,
            int rejectCountByCapacity
    ) {
        this.id = id;
        this.roomId = roomId;
        this.userId = userId;
        this.wasApproved = wasApproved;
        this.kickedAt = kickedAt;
        this.kickedCooldownUntil = kickedCooldownUntil;
        this.rejectCountByOwner = rejectCountByOwner;
        this.rejectCountByCapacity = rejectCountByCapacity;
    }

    public static RoomMember of(UUID roomId, UUID userId) {
        if (roomId == null || userId == null) {
            throw new IllegalArgumentException("roomId and userId must not be null");
        }
        return new RoomMember(null, roomId, userId, false, null, null, 0, 0);
    }

    public static RoomMember rehydrate(
            UUID id,
            UUID roomId,
            UUID userId,
            boolean wasApproved,
            Instant kickedAt,
            Instant kickedCooldownUntil,
            int rejectCountByOwner,
            int rejectCountByCapacity
    ) {
        return new RoomMember(id, roomId, userId, wasApproved, kickedAt, kickedCooldownUntil,
                rejectCountByOwner, rejectCountByCapacity);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean wasApproved() {
        return wasApproved;
    }

    public Instant getKickedAt() {
        return kickedAt;
    }

    public Instant getKickedCooldownUntil() {
        return kickedCooldownUntil;
    }

    public int getRejectCountByOwner() {
        return rejectCountByOwner;
    }

    public int getRejectCountByCapacity() {
        return rejectCountByCapacity;
    }

    public boolean isNew() {
        return id == null;
    }


    public void markApproved() {
        if (!this.wasApproved) {
            this.wasApproved = true;
            touch();
        }
    }

    public void revokeApproval() {
        if (this.wasApproved) {
            this.wasApproved = false;
            touch();
        }
    }

    public boolean isServingKickCooldown(Instant now) {
        return kickedCooldownUntil != null && now.isBefore(kickedCooldownUntil);
    }

    public void recordKick(Instant at, Instant cooldownUntil) {
        this.kickedAt = at;
        this.kickedCooldownUntil = cooldownUntil;
        revokeApproval();
        touch();
    }


    public void recordOwnerRejection() {
        this.rejectCountByOwner += 1;
        touch();
    }


    public void recordCapacityRejection() {
        this.rejectCountByCapacity += 1;
        touch();
    }

    public boolean isLockedOut() {
        return rejectCountByOwner >= REJECT_LIMIT;
    }


    public void resetRejectCounters() {
        this.rejectCountByOwner = 0;
        this.rejectCountByCapacity = 0;
        touch();
    }
}