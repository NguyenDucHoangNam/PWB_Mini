package com.pwb.backend.modules.liveroom.dto.ws;

public record RoomEvictionEvent(
        String event,
        String roomCode
) {
    public static final String EVENT_FORCE_CLOSE_ROOM_SESSIONS = "FORCE_CLOSE_ROOM_SESSIONS";
}
