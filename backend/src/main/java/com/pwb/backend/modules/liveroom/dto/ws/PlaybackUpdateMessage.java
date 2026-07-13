package com.pwb.backend.modules.liveroom.dto.ws;

import java.util.UUID;

public record PlaybackUpdateMessage(
        String event,
        Data data) {

    public record Data(
            String action,
            String playbackState,
            double currentTime,
            long clientSendTime,
            UUID lastUpdatedBy) {
    }
}