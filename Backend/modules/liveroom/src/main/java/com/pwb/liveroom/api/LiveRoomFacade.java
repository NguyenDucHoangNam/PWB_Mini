package com.pwb.liveroom.api;

import com.pwb.liveroom.api.dto.request.CreateLiveRoomRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomExistsResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomSummaryResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomViewerStatusResponse;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LiveRoomFacade {

    LiveRoomResponse createRoom(UUID hostUserId, CreateLiveRoomRequest request);

    Page<LiveRoomSummaryResponse> listMyRooms(UUID hostUserId, LiveRoomStatus status, Pageable pageable);

    LiveRoomResponse getRoom(UUID hostUserId, String roomCode);

    void endRoom(UUID hostUserId, String roomCode);

    LiveRoomExistsResponse checkRoomExists(String roomCode);

    LiveRoomViewerStatusResponse getViewerStatus(UUID viewerUserId, String roomCode);
}