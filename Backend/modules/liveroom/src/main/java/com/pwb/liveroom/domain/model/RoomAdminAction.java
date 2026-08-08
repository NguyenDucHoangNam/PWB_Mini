package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.AdminActionType;

import java.time.Instant;
import java.util.UUID;


public record RoomAdminAction(
        UUID id,
        UUID roomId,
        UUID cycleId,
        UUID actorUserId,
        UUID targetUserId,
        AdminActionType actionType,
        String reason,
        Instant createdAt
) {

    public static RoomAdminAction record(
            UUID roomId,
            UUID cycleId,
            UUID actorUserId,
            UUID targetUserId,
            AdminActionType actionType,
            String reason,
            Instant createdAt
    ) {
        return new RoomAdminAction(null, roomId, cycleId, actorUserId, targetUserId,
                actionType, reason, createdAt);
    }
}