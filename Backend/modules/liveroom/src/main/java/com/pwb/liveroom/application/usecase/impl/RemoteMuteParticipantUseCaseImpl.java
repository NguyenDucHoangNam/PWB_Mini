package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.ModerateParticipantCommand;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.usecase.RemoteMuteParticipantUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.enums.AdminActionType;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.RoomAdminAction;
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
public class RemoteMuteParticipantUseCaseImpl implements RemoteMuteParticipantUseCase {

    private final RoomLoader roomLoader;
    private final ParticipantRepository participantRepository;
    private final RoomAdminActionRepository adminActionRepository;
    private final LiveroomEventPublisher eventPublisher;
    private final ParticipationViewFactory viewFactory;
    private final LiveroomConfig config;

    @Override
    @Transactional
    public ParticipantView execute(ModerateParticipantCommand command) {
        LiveRoom room = roomLoader.requireOwned(command.roomId(), command.actorId());
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        Participant participant = participantRepository
                .findByCycleIdAndUserId(room.getCurrentCycleId(), command.targetUserId())
                .filter(Participant::isInRoom)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.PARTICIPANT_NOT_FOUND));

        Instant now = Instant.now();
        participant.muteByOwner(now, now.plus(config.getModeration().getMicUnmuteCooldown()));
        Participant muted = participantRepository.save(participant);

        adminActionRepository.save(RoomAdminAction.record(
                room.getId(),
                room.getCurrentCycleId(),
                command.actorId(),
                command.targetUserId(),
                AdminActionType.REMOTE_MUTE,
                command.reason(),
                now
        ));

        eventPublisher.broadcastToRoom(RoomEvents.micMutedByOwner(room, muted, command.actorId()));

        log.info("Participant muted by owner: roomId={} actorId={} targetUserId={} cooldownUntil={}",
                room.getId(), command.actorId(), command.targetUserId(), muted.getMicUnmuteCooldownUntil());
        return viewFactory.toView(muted, false, false);
    }
}