package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.RoomAdminAction;

import java.util.List;
import java.util.UUID;

public interface RoomAdminActionRepository {

    RoomAdminAction save(RoomAdminAction action);

    List<RoomAdminAction> findAllByRoomId(UUID roomId);
}