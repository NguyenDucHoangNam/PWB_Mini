package com.pwb.liveroom.api.dto.response;

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
public class LiveRoomJoinResponse {

    private String roomCode;
    private String title;
    private UUID hostUserId;
    private UUID participantId;
    private String displayName;
    private String roleAtJoin;
    private Instant joinedAt;
    private int currentParticipantCount;
    private int maxParticipants;
    private int availableSlots;
}