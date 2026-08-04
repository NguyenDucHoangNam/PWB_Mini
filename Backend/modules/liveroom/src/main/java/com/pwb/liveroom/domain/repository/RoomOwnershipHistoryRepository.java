package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.RoomOwnershipHistory;

import java.util.List;
import java.util.UUID;

public interface RoomOwnershipHistoryRepository {

    RoomOwnershipHistory save(RoomOwnershipHistory history);

    List<RoomOwnershipHistory> findAllByRoomId(UUID roomId);
}