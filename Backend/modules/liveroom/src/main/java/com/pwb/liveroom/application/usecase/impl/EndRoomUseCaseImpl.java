package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.RoomTermination;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.EndRoomUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class EndRoomUseCaseImpl implements EndRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomTermination roomTermination;
    private final LiveroomEventPublisher eventPublisher;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional
    public RoomView execute(UUID actorId, UUID roomId) {
        LiveRoom room = liveRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isOwnedBy(actorId)) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }
        if (room.isEnded()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        LiveRoom ended = roomTermination.terminate(room, EndedReason.MANUAL, Instant.now());
        eventPublisher.broadcastToRoom(RoomEvents.roomManuallyEnded(ended));

        log.info("Live room ended manually: roomId={} ownerId={}", ended.getId(), ended.getOwnerId());
        return roomViewFactory.toView(ended);
    }
}