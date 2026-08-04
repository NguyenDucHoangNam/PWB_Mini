package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.RoomSessionCycle;

import java.util.Optional;
import java.util.UUID;

public interface RoomSessionCycleRepository {

    RoomSessionCycle save(RoomSessionCycle cycle);

    Optional<RoomSessionCycle> findById(UUID id);


    Optional<RoomSessionCycle> findOpenByRoomId(UUID roomId);


    int findHighestCycleNumber(UUID roomId);
}