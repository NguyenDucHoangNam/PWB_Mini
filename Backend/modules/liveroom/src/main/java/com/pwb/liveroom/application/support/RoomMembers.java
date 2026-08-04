package com.pwb.liveroom.application.support;

import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomMembers {

    private final RoomMemberRepository roomMemberRepository;


    public RoomMember loadOrCreate(UUID roomId, UUID userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(() -> roomMemberRepository.save(RoomMember.of(roomId, userId)));
    }

    public RoomMember save(RoomMember member) {
        return roomMemberRepository.save(member);
    }


    public void resetRejectCounters(UUID roomId) {
        roomMemberRepository.resetRejectCountersForRoom(roomId);
    }
}