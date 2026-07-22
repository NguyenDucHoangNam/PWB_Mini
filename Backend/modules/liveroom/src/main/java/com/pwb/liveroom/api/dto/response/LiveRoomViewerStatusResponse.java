package com.pwb.liveroom.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.JoinRequestStatus;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveRoomViewerStatusResponse {

    private UUID viewerUserId;
    private boolean host;
    private boolean participant;
    private boolean pendingRequest;
    private JoinRequestStatus pendingStatus;
    private UUID pendingRequestId;
    private LiveRoomStatus roomStatus;
    private LiveRoomMode roomMode;
    private String roomCode;
    private UUID hostUserId;
    private Instant createdAt;
}
