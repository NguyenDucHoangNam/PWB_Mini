package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.core.model.JoinRequestStatus;
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
public class LiveRoomJoinRequestResponse {

    private UUID id;
    private String roomCode;
    private UUID userId;
    private String displayName;
    private String message;
    private JoinRequestStatus status;
    private String decisionReason;
    private UUID decidedByUserId;
    private Instant decidedAt;
    private Instant createdAt;
}
