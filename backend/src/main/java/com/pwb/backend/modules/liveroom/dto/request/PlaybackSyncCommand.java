package com.pwb.backend.modules.liveroom.dto.request;

public record PlaybackSyncCommand(
        String action,
        double currentTime,
        long clientSendTime) {
}