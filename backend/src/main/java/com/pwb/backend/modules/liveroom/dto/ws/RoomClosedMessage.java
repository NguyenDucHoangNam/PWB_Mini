package com.pwb.backend.modules.liveroom.dto.ws;

public record RoomClosedMessage(
        String event,
        Data data
) {
    public record Data(
            String roomCode,
            String reason
    ) {
    }
}
