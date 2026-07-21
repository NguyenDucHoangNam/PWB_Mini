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
public class LiveRoomSummaryResponse {

    private UUID id;
    private String roomCode;
    private String title;
    private LiveRoomMode mode;
    private LiveRoomStatus status;
    private int maxParticipants;
    private int currentParticipantCount;
    private Instant createdAt;
    private Instant endedAt;
}