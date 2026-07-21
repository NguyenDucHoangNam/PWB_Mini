package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LiveRoomService {

    LiveRoom createRoom(
            UUID hostUserId,
            String title,
            String description,
            LiveRoomMode mode,
            String rawPassword,
            Integer maxParticipants,
            java.time.Instant scheduledStartAt);

    Page<LiveRoom> listMyRooms(UUID hostUserId, LiveRoomStatus status, Pageable pageable);

    LiveRoom getRoomByCode(UUID hostUserId, String roomCode);

    LiveRoom updateRoomSettings(
            UUID hostUserId,
            String roomCode,
            String title,
            String description,
            LiveRoomMode mode,
            String rawPassword,
            Integer maxParticipants);

    void endRoom(UUID hostUserId, String roomCode);

    boolean existsActiveRoomByCode(String roomCode);

    LiveRoom getRoomAsParticipant(String roomCode);
}