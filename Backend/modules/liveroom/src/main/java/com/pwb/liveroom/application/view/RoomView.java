package com.pwb.liveroom.application.view;

import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.enums.RoomStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomView(
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
}