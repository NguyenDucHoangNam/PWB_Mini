package com.pwb.backend.modules.liveroom.outbox;

import java.time.Instant;
import java.util.UUID;

public record RoomLifecycleEndedEvent(
        UUID roomId,
        String roomCode,
        UUID hostId,
        Instant startedAt,
        Instant endedAt,
        int maxGuests,
        String reason
) {
}
