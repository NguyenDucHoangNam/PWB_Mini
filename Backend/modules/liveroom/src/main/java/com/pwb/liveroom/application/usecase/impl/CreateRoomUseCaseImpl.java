package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.CreateRoomCommand;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.Admissions;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.support.SessionCycleStarter;
import com.pwb.liveroom.application.usecase.CreateRoomUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.OwnershipChangeType;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.RoomOwnershipHistory;
import com.pwb.liveroom.domain.model.vo.RoomCode;
import com.pwb.liveroom.domain.model.vo.RoomName;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.RoomOwnershipHistoryRepository;
import com.pwb.liveroom.domain.service.RoomCodeGenerator;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateRoomUseCaseImpl implements CreateRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomOwnershipHistoryRepository ownershipHistoryRepository;
    private final RoomCodeGenerator roomCodeGenerator;
    private final SessionCycleStarter sessionCycleStarter;
    private final Admissions admissions;
    private final RoomViewFactory roomViewFactory;
    private final LiveroomConfig config;

    @Override
    @Transactional
    public RoomView execute(CreateRoomCommand command) {
        if (!command.actor().isPro()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.PRO_REQUIRED);
        }

        RoomName roomName = parseName(command.roomName());
        if (liveRoomRepository.existsByOwnerIdAndNormalizedName(command.actor().userId(), roomName.normalized())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NAME_DUPLICATE);
        }

        int capacity = command.maxParticipants() != null
                ? command.maxParticipants()
                : config.getRoom().getDefaultCapacity();
        int graceSeconds = resolveGrace(command.ownerGraceSeconds());

        Instant now = Instant.now();
        LiveRoom room = liveRoomRepository.save(LiveRoom.create(
                command.actor().userId(),
                allocateRoomCode(),
                roomName,
                capacity,
                graceSeconds
        ));


        sessionCycleStarter.open(room, now);


        admissions.admit(room, command.actor().userId(), command.actor().email(), ParticipantRole.OWNER, now);
        LiveRoom withCycle = liveRoomRepository.save(room);

        ownershipHistoryRepository.save(RoomOwnershipHistory.record(
                withCycle.getId(),
                withCycle.getOwnerId(),
                OwnershipChangeType.INITIAL_CREATE,
                null,
                now
        ));

        log.info("Live room created: roomId={} ownerId={} roomCode={} capacity={} graceSeconds={}",
                withCycle.getId(), withCycle.getOwnerId(), withCycle.getRoomCode().value(), capacity, graceSeconds);
        return roomViewFactory.toView(withCycle);
    }

    private RoomName parseName(String raw) {
        try {
            return RoomName.of(raw);
        } catch (IllegalArgumentException ex) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NAME_INVALID, ex.getMessage());
        }
    }

    private int resolveGrace(Integer requested) {
        if (requested == null) {
            return LiveRoom.DEFAULT_GRACE_SECONDS;
        }
        if (requested < LiveRoom.MIN_GRACE_SECONDS || requested > LiveRoom.MAX_GRACE_SECONDS) {
            throw new LiveroomBusinessException(LiveroomErrorCode.GRACE_INVALID);
        }
        return requested;
    }


    private RoomCode allocateRoomCode() {
        int attempts = config.getRoom().getCodeGenerationAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            RoomCode candidate = roomCodeGenerator.generate();
            if (!liveRoomRepository.existsByRoomCode(candidate.value())) {
                return candidate;
            }
            log.warn("Room code collision on attempt {}/{}", attempt, attempts);
        }
        throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_CODE_GENERATION_FAILED);
    }
}