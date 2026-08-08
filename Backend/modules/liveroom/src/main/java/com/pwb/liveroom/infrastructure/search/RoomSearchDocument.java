package com.pwb.liveroom.infrastructure.search;

import com.pwb.liveroom.domain.model.LiveRoom;

import java.time.Instant;
import java.util.UUID;

public record RoomSearchDocument(
        UUID id,
        UUID ownerId,
        String roomName,
        String roomCode,
        String status,
        int currentParticipantCount,
        int maxParticipants,
        Instant createdAt
) {

    public static RoomSearchDocument from(LiveRoom room) {
        return new RoomSearchDocument(
                room.getId(),
                room.getOwnerId(),
                room.getRoomName().value(),
                room.getRoomCode().value(),
                room.getStatus().name(),
                room.getCurrentParticipantCount(),
                room.getMaxParticipants(),
                room.getCreatedAt()
        );
    }
}