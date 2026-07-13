package com.pwb.backend.modules.liveroom.dto.response;

import java.util.List;
import java.util.UUID;

public record PlaybackStateResponse(
        UUID activeSourceId,
        String title,
        double duration,
        List<Double> waveform,
        long eventTimestamp,
        String playbackState,
        double currentTime,
        long clientSendTime,
        long serverTimestamp,
        boolean globalDelegation,
        List<UUID> delegatedUserIds) {
}