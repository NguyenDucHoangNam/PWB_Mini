package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.enums.RoomStatus;

import java.time.Instant;
import java.util.UUID;


public record RoomResponse(
        UUID id,
        UUID ownerId,
        String roomCode,
        String roomCodeDisplay,
        String roomName,
        RoomStatus status,
        int maxParticipants,
        int effectiveMaxParticipants,
        int currentParticipantCount,
        int ownerGraceSeconds,
        boolean reservedOwnerSlot,
        Instant ownerLeftAt,
        UUID currentCycleId,
        int reopenedCount,
        Instant lastReopenedAt,
        Instant previousEndedAt,
        Instant endedAt,
        EndedReason endedReason,
        boolean canUndoEnd,
        Instant createdAt,
        Instant updatedAt
) {

    public static RoomResponse from(RoomView view) {
        return new RoomResponse(
                view.id(),
                view.ownerId(),
                view.roomCode(),
                view.roomCodeDisplay(),
                view.roomName(),
                view.status(),
                view.maxParticipants(),
                view.effectiveMaxParticipants(),
                view.currentParticipantCount(),
                view.ownerGraceSeconds(),
                view.reservedOwnerSlot(),
                view.ownerLeftAt(),
                view.currentCycleId(),
                view.reopenedCount(),
                view.lastReopenedAt(),
                view.previousEndedAt(),
                view.endedAt(),
                view.endedReason(),
                view.canUndoEnd(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}