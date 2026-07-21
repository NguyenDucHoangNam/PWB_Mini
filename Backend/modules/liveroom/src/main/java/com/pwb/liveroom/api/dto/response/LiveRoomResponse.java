package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveRoomResponse {

    private UUID id;
    private UUID hostUserId;
    private String roomCode;
    private String title;
    private String description;
    private LiveRoomMode mode;
    private int maxParticipants;
    private int currentParticipantCount;
    private int availableSlots;
    private LiveRoomStatus status;
    private Instant scheduledStartAt;
    private Instant startedAt;
    private Instant endedAt;
    private Instant createdAt;
}
