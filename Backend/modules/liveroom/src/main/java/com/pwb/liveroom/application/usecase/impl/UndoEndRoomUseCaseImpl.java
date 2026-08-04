package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.support.SessionCycleStarter;
import com.pwb.liveroom.application.usecase.UndoEndRoomUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class UndoEndRoomUseCaseImpl implements UndoEndRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final ParticipantRepository participantRepository;
    private final SessionCycleStarter sessionCycleStarter;
    private final LiveroomEventPublisher eventPublisher;
    private final RoomViewFactory roomViewFactory;
    private final LiveroomConfig config;

    @Override
    @Transactional
    public RoomView execute(UUID actorId, UUID roomId) {
        LiveRoom room = liveRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isOwnedBy(actorId)) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }

        Instant now = Instant.now();
        if (!room.canUndoEnd(now, config.getRoom().getUndoEndWindow())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.UNDO_WINDOW_EXPIRED);
        }



        Instant endedAt = room.getEndedAt();
        UUID cycleId = room.getCurrentCycleId();

        room.undoEnd(now, config.getRoom().getUndoEndWindow());
        sessionCycleStarter.reviveCurrent(room);
        room.restoreOccupancy(restoreOccupants(cycleId, endedAt));
        LiveRoom revived = liveRoomRepository.save(room);

        eventPublisher.broadcastToRoom(RoomEvents.roomRevived(revived, now, endedAt));
        eventPublisher.broadcastToRoom(RoomEvents.capacityChanged(revived));

        log.info("Live room revived within undo window: roomId={} ownerId={} restoredOccupants={}",
                revived.getId(), revived.getOwnerId(), revived.getCurrentParticipantCount());
        return roomViewFactory.toView(revived);
    }

    private int restoreOccupants(UUID cycleId, Instant endedAt) {
        if (cycleId == null || endedAt == null) {
            return 0;
        }
        List<Participant> closed = participantRepository.findClosedWithRoomAt(cycleId, endedAt);
        closed.forEach(Participant::restoreAfterRoomRevived);
        participantRepository.saveAll(closed);
        return closed.size();
    }
}