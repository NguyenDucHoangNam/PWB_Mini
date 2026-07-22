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
public class ParticipantSummaryResponse {

    private UUID participantId;
    private UUID userId;
    private String displayName;
    private String roleAtJoin;
    private Instant joinedAt;
    private boolean micMuted;
    private boolean cameraOff;
    private Instant lastSeenAt;
}
