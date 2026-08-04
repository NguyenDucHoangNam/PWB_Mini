package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.OwnershipChangeType;

import java.time.Instant;
import java.util.UUID;


public record RoomOwnershipHistory(
        UUID id,
        UUID roomId,
        UUID ownerUserId,
        OwnershipChangeType changeType,
        String reason,
        Instant changedAt
) {

    public static RoomOwnershipHistory record(
            UUID roomId,
            UUID ownerUserId,
            OwnershipChangeType changeType,
            String reason,
            Instant changedAt
    ) {
        return new RoomOwnershipHistory(null, roomId, ownerUserId, changeType, reason, changedAt);
    }
}