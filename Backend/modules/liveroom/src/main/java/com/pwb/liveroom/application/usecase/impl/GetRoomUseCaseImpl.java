package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.GetRoomUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class GetRoomUseCaseImpl implements GetRoomUseCase {

    private final RoomLoader roomLoader;
    private final ParticipantRepository participantRepository;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional(readOnly = true)
    public RoomView execute(UUID actorId, UUID roomId) {
        LiveRoom room = roomLoader.require(roomId);
        if (!room.isOwnedBy(actorId) && !isInRoom(room, actorId)) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }
        return roomViewFactory.toView(room);
    }

    private boolean isInRoom(LiveRoom room, UUID actorId) {
        return room.getCurrentCycleId() != null
                && participantRepository.findByCycleIdAndUserId(room.getCurrentCycleId(), actorId)
                        .filter(Participant::isInRoom)
                        .isPresent();
    }
}