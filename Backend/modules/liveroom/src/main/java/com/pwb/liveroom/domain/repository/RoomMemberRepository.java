package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.RoomMember;

import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository {

    RoomMember save(RoomMember member);

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);


    void resetRejectCountersForRoom(UUID roomId);
}