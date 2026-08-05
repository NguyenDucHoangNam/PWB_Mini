package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.RoomAudioUrlView;

import java.time.Instant;
import java.util.UUID;

public record RoomAudioUrlResponse(
        UUID songId,
        String url,
        Instant expiresAt
) {

    public static RoomAudioUrlResponse from(RoomAudioUrlView view) {
        return new RoomAudioUrlResponse(view.songId(), view.url(), view.expiresAt());
    }
}