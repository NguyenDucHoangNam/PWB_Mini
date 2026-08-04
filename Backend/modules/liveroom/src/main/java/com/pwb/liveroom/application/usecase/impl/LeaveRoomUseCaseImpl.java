package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.OwnerPresenceAnnouncer;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.usecase.LeaveRoomUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRoomUseCaseImpl implements LeaveRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final ParticipantRepository participantRepository;
    private final LiveroomEventPublisher eventPublisher;
    private final OwnerPresenceAnnouncer ownerPresenceAnnouncer;
    private final Playbacks playbacks;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public ParticipantView execute(UUID actorId, UUID roomId) {
        LiveRoom room = liveRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        Participant participant = participantRepository
                .findByCycleIdAndUserId(room.getCurrentCycleId(), actorId)
                .filter(Participant::isInRoom)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.NOT_IN_SESSION));

        Instant now = Instant.now();
        participant.leave(now);
        Participant left = participantRepository.save(participant);

        room.releaseSlot();
        boolean ownerLeaving = room.isOwnedBy(actorId);
        if (ownerLeaving) {
            room.markOwnerLeft(now);
        }
        liveRoomRepository.save(room);

        eventPublisher.broadcastToRoom(RoomEvents.participantLeft(room, left));
        if (ownerLeaving) {


            playbacks.pauseForOwnerAbsence(room, now);


            ownerPresenceAnnouncer.ownerLeft(roomId);
        }
        eventPublisher.broadcastToRoom(RoomEvents.capacityChanged(room));

        log.info("User left room: roomId={} userId={} owner={} count={}/{} graceExpiresAt={}",
                roomId, actorId, participant.isOwner(),
                room.getCurrentParticipantCount(), room.effectiveMaxParticipants(),
                room.ownerGraceExpiresAt());
        return viewFactory.toView(left, false, false);
    }
}