package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.Actor;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.Admissions;
import com.pwb.liveroom.application.support.OwnerPresenceAnnouncer;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.JoinRoomUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class JoinRoomUseCaseImpl implements JoinRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final Admissions admissions;
    private final RoomMembers roomMembers;
    private final LiveroomEventPublisher eventPublisher;
    private final OwnerPresenceAnnouncer ownerPresenceAnnouncer;
    private final Playbacks playbacks;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public ParticipantView execute(Actor actor, UUID roomId) {
        LiveRoom room = liveRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isActive()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        Instant now = Instant.now();
        boolean isOwner = room.isOwnedBy(actor.userId());
        boolean ownerReturning = false;
        RoomMember member = roomMembers.loadOrCreate(roomId, actor.userId());

        if (!isOwner) {
            if (member.isServingKickCooldown(now)) {
                long minutes = Math.max(
                        1, Duration.between(now, member.getKickedCooldownUntil()).toMinutes() + 1);
                throw new LiveroomBusinessException(
                        LiveroomErrorCode.KICKED_COOLDOWN, Map.of("minutes", minutes));
            }
            if (!member.wasApproved()) {
                throw new LiveroomBusinessException(LiveroomErrorCode.APPROVAL_REQUIRED);
            }
        } else if (room.isOwnerAbsent()) {


            room.markOwnerReturned();
            ownerReturning = true;
        }

        Participant participant = admissions.admit(
                room,
                actor.userId(),
                actor.email(),
                isOwner ? ParticipantRole.OWNER : ParticipantRole.PARTICIPANT,
                now
        );
        liveRoomRepository.save(room);

        if (ownerReturning) {


            ownerPresenceAnnouncer.ownerReturned(room, now);
        }
        eventPublisher.broadcastToRoom(RoomEvents.participantJoined(room, participant));



        playbacks.sendCurrentTo(actor.userId(), room, now);
        eventPublisher.broadcastToRoom(RoomEvents.capacityChanged(room));
        if (!room.hasFreeSlot()) {
            eventPublisher.broadcastToRoom(RoomEvents.capacityReached(room));
        }

        log.info("User joined room: roomId={} userId={} owner={} count={}/{}",
                roomId, actor.userId(), isOwner,
                room.getCurrentParticipantCount(), room.effectiveMaxParticipants());
        return viewFactory.toView(participant, false, false);
    }
}