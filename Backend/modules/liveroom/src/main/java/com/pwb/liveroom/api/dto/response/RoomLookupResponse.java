package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.RoomLookupView;
import com.pwb.liveroom.domain.enums.RoomStatus;

import java.util.UUID;


public record RoomLookupResponse(
        UUID roomId,
        String roomCode,
        String roomCodeDisplay,
        String roomName,
        RoomStatus status,
        int maxParticipants,
        int currentParticipantCount,
        boolean full
) {

    public static RoomLookupResponse from(RoomLookupView view) {
        return new RoomLookupResponse(
                view.roomId(),
                view.roomCode(),
                view.roomCodeDisplay(),
                view.roomName(),
                view.status(),
                view.maxParticipants(),
                view.currentParticipantCount(),
                view.full()
        );
    }
}