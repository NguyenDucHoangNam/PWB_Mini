package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.UpdateMediaStateCommand;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.usecase.UpdateMediaStateUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.enums.MicState;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateMediaStateUseCaseImpl implements UpdateMediaStateUseCase {

    private final RoomLoader roomLoader;
    private final ParticipantRepository participantRepository;
    private final LiveroomEventPublisher eventPublisher;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public ParticipantView execute(UpdateMediaStateCommand command) {
        LiveRoom room = roomLoader.require(command.roomId());
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        Participant participant = participantRepository
                .findByCycleIdAndUserId(room.getCurrentCycleId(), command.actorId())
                .filter(Participant::isInRoom)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.NOT_IN_SESSION));

        Instant now = Instant.now();
        boolean wasMutedByOwner = participant.getMicState() == MicState.MUTED_BY_OWNER;
        boolean cameraOn = command.cameraOn() != null ? command.cameraOn() : participant.isCameraOn();
        boolean micOn = command.micOn() != null ? command.micOn() : participant.isMicOn();



        if (micOn && participant.isMicUnmuteBlocked(now)) {
            long seconds = Math.max(1,
                    Duration.between(now, participant.getMicUnmuteCooldownUntil()).toSeconds() + 1);
            throw new LiveroomBusinessException(
                    LiveroomErrorCode.MIC_MUTE_COOLDOWN, Map.of("seconds", seconds));
        }

        participant.applyMediaState(cameraOn, micOn, now);
        Participant updated = participantRepository.save(participant);

        eventPublisher.broadcastToRoom(RoomEvents.mediaStateChanged(room, updated));

        if (wasMutedByOwner && updated.isMicOn()) {
            eventPublisher.broadcastToRoom(RoomEvents.micUnmuted(room, updated, now));
        }

        log.debug("Media state updated: roomId={} userId={} cameraOn={} micOn={} micState={}",
                room.getId(), command.actorId(), updated.isCameraOn(), updated.isMicOn(), updated.getMicState());
        return viewFactory.toView(updated, false, false);
    }
}