package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomSessions {

    private final LiveRoomRepository liveRoomRepository;
    private final ParticipantRepository participantRepository;

    public LiveRoom requireActiveRoom(UUID roomId) {
        LiveRoom room = liveRoomRepository.findById(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }
        return room;
    }

    public Participant requireInRoom(LiveRoom room, UUID actorId) {
        return findInRoom(room, actorId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.NOT_IN_SESSION));
    }

    public Participant requireTargetInRoom(LiveRoom room, UUID targetUserId) {
        return findInRoom(room, targetUserId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.PARTICIPANT_NOT_FOUND));
    }

    private java.util.Optional<Participant> findInRoom(LiveRoom room, UUID userId) {
        return participantRepository.findByCycleIdAndUserId(room.getCurrentCycleId(), userId)
                .filter(Participant::isInRoom);
    }
}
