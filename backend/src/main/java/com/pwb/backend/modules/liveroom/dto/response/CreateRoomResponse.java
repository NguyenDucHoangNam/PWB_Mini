package com.pwb.backend.modules.liveroom.dto.response;

import com.pwb.backend.modules.liveroom.entity.Room;
import com.pwb.backend.modules.liveroom.enums.RoomMode;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;

import java.time.Instant;
import java.util.UUID;

public record CreateRoomResponse(
        String roomCode,
        UUID hostId,
        String hostDisplayName,
        RoomMode mode,
        RoomStatus status,
        int maxParticipants,
        Instant createdAt) {

    public static CreateRoomResponse from(Room room, String hostDisplayName) {
        return new CreateRoomResponse(
                room.getRoomCode(),
                room.getHostId(),
                hostDisplayName,
                room.getMode(),
                room.getStatus(),
                room.getMaxParticipants(),
                room.getCreatedAt());
    }
}