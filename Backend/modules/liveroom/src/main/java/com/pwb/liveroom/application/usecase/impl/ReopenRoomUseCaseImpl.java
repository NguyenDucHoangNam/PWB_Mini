package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.Actor;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.Admissions;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.support.SessionCycleStarter;
import com.pwb.liveroom.application.usecase.ReopenRoomUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
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
public class ReopenRoomUseCaseImpl implements ReopenRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final RoomLoader roomLoader;
    private final RoomMembers roomMembers;
    private final SessionCycleStarter sessionCycleStarter;
    private final Admissions admissions;
    private final Playbacks playbacks;
    private final LiveroomEventPublisher eventPublisher;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional
    public RoomView execute(Actor actor, UUID roomId) {
        LiveRoom room = roomLoader.requireOwned(roomId, actor.userId());
        if (!room.isEnded()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.CANNOT_REOPEN);
        }



        roomMembers.resetRejectCounters(roomId);
        joinRequestRepository.deleteAllByRoomId(roomId);


        playbacks.clear(roomId);

        Instant now = Instant.now();
        room.reopen(now);


        sessionCycleStarter.open(room, now);
        admissions.admit(room, actor.userId(), actor.email(), ParticipantRole.OWNER, now);
        LiveRoom reopened = liveRoomRepository.save(room);

        eventPublisher.broadcastToRoom(RoomEvents.roomReopened(reopened));
        eventPublisher.broadcastToRoom(RoomEvents.capacityChanged(reopened));

        log.info("Live room reopened: roomId={} ownerId={} reopenedCount={}",
                reopened.getId(), reopened.getOwnerId(), reopened.getReopenedCount());
        return roomViewFactory.toView(reopened);
    }
}