package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomLoader {

    private final LiveRoomRepository liveRoomRepository;

    public LiveRoom require(UUID roomId) {
        return liveRoomRepository.findById(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
    }


    public LiveRoom requireOwned(UUID roomId, UUID actorId) {
        LiveRoom room = require(roomId);
        if (!room.isOwnedBy(actorId)) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }
}