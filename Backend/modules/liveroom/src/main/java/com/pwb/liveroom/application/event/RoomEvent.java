package com.pwb.liveroom.application.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;


public record RoomEvent(
        LiveroomEventType type,
        UUID roomId,
        Instant timestamp,
        Map<String, Object> data
) {

    public RoomEvent {


        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}