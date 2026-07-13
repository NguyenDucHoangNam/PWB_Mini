package com.pwb.backend.modules.liveroom.dto.ws;

import java.util.List;
import java.util.UUID;

public record PlaybackChangeMessage(
        String event,
        Data data) {

    public record Data(
            UUID activeSourceId,
            String title,
            double duration,
            List<Double> waveform,
            UUID changedBy,
            long eventTimestamp) {
    }
}
