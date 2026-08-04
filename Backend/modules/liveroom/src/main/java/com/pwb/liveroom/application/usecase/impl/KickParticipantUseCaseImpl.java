package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.ModerateParticipantCommand;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RealtimeSessionEvictor;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.KickParticipantUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.enums.AdminActionType;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.RoomAdminAction;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import com.pwb.liveroom.domain.repository.RoomAdminActionRepository;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class KickParticipantUseCaseImpl implements KickParticipantUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final ParticipantRepository participantRepository;
    private final RoomAdminActionRepository adminActionRepository;
    private final RoomMembers roomMembers;
    private final LiveroomEventPublisher eventPublisher;
    private final RealtimeSessionEvictor sessionEvictor;
    private final ParticipationViewFactory viewFactory;
    private final LiveroomConfig config;

    @Override
    @Transactional
    public ParticipantView execute(ModerateParticipantCommand command) {
        if (command.actorId().equals(command.targetUserId())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.SELF_KICK_NOT_ALLOWED);
        }

        LiveRoom room = liveRoomRepository.findByIdForUpdate(command.roomId())
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isOwnedBy(command.actorId())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        Participant participant = participantRepository
                .findByCycleIdAndUserId(room.getCurrentCycleId(), command.targetUserId())
                .filter(Participant::isInRoom)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.PARTICIPANT_NOT_FOUND));

        Instant now = Instant.now();
        Instant cooldownUntil = now.plus(config.getModeration().getKickCooldown());

        participant.kick(now);
        Participant kicked = participantRepository.save(participant);

        room.releaseSlot();
        liveRoomRepository.save(room);

        RoomMember member = roomMembers.loadOrCreate(room.getId(), command.targetUserId());
        member.recordKick(now, cooldownUntil);
        roomMembers.save(member);

        adminActionRepository.save(RoomAdminAction.record(
                room.getId(),
                room.getCurrentCycleId(),
                command.actorId(),
                command.targetUserId(),
                AdminActionType.KICK,
                command.reason(),
                now
        ));

        eventPublisher.broadcastToRoom(
                RoomEvents.participantKicked(room, kicked, command.actorId(), command.reason(), cooldownUntil));
        eventPublisher.broadcastToRoom(RoomEvents.capacityChanged(room));


        sessionEvictor.evictUser(command.targetUserId());

        log.info("Participant kicked: roomId={} actorId={} targetUserId={} cooldownUntil={}",
                room.getId(), command.actorId(), command.targetUserId(), cooldownUntil);
        return viewFactory.toView(kicked, false, false);
    }
}