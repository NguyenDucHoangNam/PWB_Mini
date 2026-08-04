package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.enums.RoomStatus;
import com.pwb.liveroom.domain.model.vo.RoomCode;
import com.pwb.liveroom.domain.model.vo.RoomName;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


public final class LiveRoom extends DomainBaseEntity {

    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 7;
    public static final int DEFAULT_CAPACITY = 7;

    public static final int MIN_GRACE_SECONDS = 30;
    public static final int MAX_GRACE_SECONDS = 1800;
    public static final int DEFAULT_GRACE_SECONDS = 60;

    private final UUID id;
    private final UUID ownerId;
    private final RoomCode roomCode;
    private RoomName roomName;
    private RoomStatus status;
    private int maxParticipants;
    private int ownerGraceSeconds;
    private int currentParticipantCount;
    private boolean reservedOwnerSlot;
    private Instant ownerLeftAt;
    private UUID currentCycleId;
    private int reopenedCount;
    private Instant lastReopenedAt;
    private Instant previousEndedAt;
    private Instant endedAt;
    private EndedReason endedReason;

    private LiveRoom(
            UUID id,
            UUID ownerId,
            RoomCode roomCode,
            RoomName roomName,
            RoomStatus status,
            int maxParticipants,
            int ownerGraceSeconds,
            int currentParticipantCount,
            boolean reservedOwnerSlot,
            Instant ownerLeftAt,
            UUID currentCycleId,
            int reopenedCount,
            Instant lastReopenedAt,
            Instant previousEndedAt,
            Instant endedAt,
            EndedReason endedReason
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.status = status;
        this.maxParticipants = maxParticipants;
        this.ownerGraceSeconds = ownerGraceSeconds;
        this.currentParticipantCount = currentParticipantCount;
        this.reservedOwnerSlot = reservedOwnerSlot;
        this.ownerLeftAt = ownerLeftAt;
        this.currentCycleId = currentCycleId;
        this.reopenedCount = reopenedCount;
        this.lastReopenedAt = lastReopenedAt;
        this.previousEndedAt = previousEndedAt;
        this.endedAt = endedAt;
        this.endedReason = endedReason;
    }


    public static LiveRoom create(
            UUID ownerId,
            RoomCode roomCode,
            RoomName roomName,
            int maxParticipants,
            int ownerGraceSeconds
    ) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (roomCode == null) {
            throw new IllegalArgumentException("roomCode must not be null");
        }
        if (roomName == null) {
            throw new IllegalArgumentException("roomName must not be null");
        }
        requireValidCapacity(maxParticipants);
        requireValidGrace(ownerGraceSeconds);


        return new LiveRoom(
                null,
                ownerId,
                roomCode,
                roomName,
                RoomStatus.ACTIVE,
                maxParticipants,
                ownerGraceSeconds,
                0,
                false,
                null,
                null,
                0,
                null,
                null,
                null,
                null
        );
    }

    public static LiveRoom rehydrate(
            UUID id,
            UUID ownerId,
            String roomCode,
            String roomName,
            RoomStatus status,
            int maxParticipants,
            int ownerGraceSeconds,
            int currentParticipantCount,
            boolean reservedOwnerSlot,
            Instant ownerLeftAt,
            UUID currentCycleId,
            int reopenedCount,
            Instant lastReopenedAt,
            Instant previousEndedAt,
            Instant endedAt,
            EndedReason endedReason
    ) {
        return new LiveRoom(
                id,
                ownerId,
                RoomCode.of(roomCode),
                RoomName.of(roomName),
                status,
                maxParticipants,
                ownerGraceSeconds,
                currentParticipantCount,
                reservedOwnerSlot,
                ownerLeftAt,
                currentCycleId,
                reopenedCount,
                lastReopenedAt,
                previousEndedAt,
                endedAt,
                endedReason
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public RoomCode getRoomCode() {
        return roomCode;
    }

    public RoomName getRoomName() {
        return roomName;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public int getOwnerGraceSeconds() {
        return ownerGraceSeconds;
    }

    public int getCurrentParticipantCount() {
        return currentParticipantCount;
    }

    public boolean isReservedOwnerSlot() {
        return reservedOwnerSlot;
    }

    public Instant getOwnerLeftAt() {
        return ownerLeftAt;
    }

    public UUID getCurrentCycleId() {
        return currentCycleId;
    }

    public int getReopenedCount() {
        return reopenedCount;
    }

    public Instant getLastReopenedAt() {
        return lastReopenedAt;
    }

    public Instant getPreviousEndedAt() {
        return previousEndedAt;
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

    public boolean isActive() {
        return status == RoomStatus.ACTIVE;
    }

    public boolean isEnded() {
        return status == RoomStatus.ENDED;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }


    public int effectiveMaxParticipants() {
        return reservedOwnerSlot ? maxParticipants - 1 : maxParticipants;
    }

    public boolean hasFreeSlot() {
        return currentParticipantCount < effectiveMaxParticipants();
    }


    public void admitParticipant() {
        if (!hasFreeSlot()) {
            throw new RoomStateException("Room is full");
        }
        this.currentParticipantCount += 1;
        touch();
    }


    public void restoreOccupancy(int occupiedSlots) {
        if (occupiedSlots < 0) {
            throw new IllegalArgumentException("occupiedSlots must not be negative");
        }
        this.currentParticipantCount = occupiedSlots;
        touch();
    }

    public void releaseSlot() {
        if (currentParticipantCount > 0) {
            this.currentParticipantCount -= 1;
            touch();
        }
    }


    public void markOwnerLeft(Instant at) {
        this.ownerLeftAt = at;
        this.reservedOwnerSlot = true;
        touch();
    }

    public void markOwnerReturned() {
        this.ownerLeftAt = null;
        this.reservedOwnerSlot = false;
        touch();
    }

    public boolean isOwnerAbsent() {
        return ownerLeftAt != null;
    }


    public Instant ownerGraceExpiresAt() {
        return ownerLeftAt == null ? null : ownerLeftAt.plusSeconds(ownerGraceSeconds);
    }

    public void attachCycle(UUID cycleId) {
        if (cycleId == null) {
            throw new IllegalArgumentException("cycleId must not be null");
        }
        this.currentCycleId = cycleId;
        touch();
    }

    public void end(EndedReason reason, Instant at) {
        if (reason == null) {
            throw new IllegalArgumentException("endedReason must not be null");
        }
        if (isEnded()) {
            throw new RoomStateException("Room is already ended");
        }
        this.status = RoomStatus.ENDED;
        this.endedAt = at;
        this.endedReason = reason;
        this.ownerLeftAt = null;
        this.reservedOwnerSlot = false;
        this.currentParticipantCount = 0;
        touch();
    }


    public boolean canUndoEnd(Instant now, Duration window) {
        return isEnded()
                && endedReason == EndedReason.MANUAL
                && endedAt != null
                && !now.isAfter(endedAt.plus(window));
    }

    public void undoEnd(Instant now, Duration window) {
        if (!canUndoEnd(now, window)) {
            throw new RoomStateException("Room cannot be revived at this point");
        }
        this.status = RoomStatus.ACTIVE;
        this.endedAt = null;
        this.endedReason = null;
        touch();
    }


    public void reopen(Instant now) {
        if (!isEnded()) {
            throw new RoomStateException("Only an ended room can be reopened");
        }
        this.previousEndedAt = this.endedAt;
        this.status = RoomStatus.ACTIVE;
        this.endedAt = null;
        this.endedReason = null;
        this.reopenedCount += 1;
        this.lastReopenedAt = now;
        this.currentParticipantCount = 0;
        this.reservedOwnerSlot = false;
        this.ownerLeftAt = null;
        this.currentCycleId = null;
        touch();
    }

    private static void requireValidCapacity(int maxParticipants) {
        if (maxParticipants < MIN_CAPACITY || maxParticipants > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "maxParticipants must be between " + MIN_CAPACITY + " and " + MAX_CAPACITY);
        }
    }

    private static void requireValidGrace(int ownerGraceSeconds) {
        if (ownerGraceSeconds < MIN_GRACE_SECONDS || ownerGraceSeconds > MAX_GRACE_SECONDS) {
            throw new IllegalArgumentException(
                    "ownerGraceSeconds must be between " + MIN_GRACE_SECONDS + " and " + MAX_GRACE_SECONDS);
        }
    }

    public static class RoomStateException extends RuntimeException {
        public RoomStateException(String message) {
            super(message);
        }
    }
}