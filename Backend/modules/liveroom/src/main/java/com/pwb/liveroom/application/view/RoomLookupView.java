package com.pwb.liveroom.application.view;

import com.pwb.liveroom.domain.enums.RoomStatus;

import java.util.UUID;


public record RoomLookupView(
        UUID roomId,
        String roomCode,
        String roomCodeDisplay,
        String roomName,
        RoomStatus status,
        int maxParticipants,
        int currentParticipantCount,
        boolean full
) {
}