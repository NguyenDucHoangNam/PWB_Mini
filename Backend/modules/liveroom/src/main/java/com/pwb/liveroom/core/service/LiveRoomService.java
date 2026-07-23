package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LiveRoomService {

    LiveRoom createRoom(
            UUID hostUserId,
            String hostDisplayName,
            String title,
            String description,
            LiveRoomMode mode,
            Integer maxParticipants);

    Page<LiveRoom> listMyRooms(UUID hostUserId, LiveRoomStatus status, Pageable pageable);

    LiveRoom getRoomAsHost(UUID hostUserId, String roomCode);

    LiveRoom getRoomPublicInfo(String roomCode);

    void endRoom(UUID hostUserId, String roomCode);

    boolean existsActiveRoomByCode(String roomCode);
}
