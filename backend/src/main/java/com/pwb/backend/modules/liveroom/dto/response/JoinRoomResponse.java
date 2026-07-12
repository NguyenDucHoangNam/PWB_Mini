package com.pwb.backend.modules.liveroom.dto.response;

import com.pwb.backend.modules.liveroom.enums.RoomMode;

public record JoinRoomResponse(
        String status,
        boolean accessGranted,
        String roomCode,
        RoomMode mode,
        String temporaryToken) {
}