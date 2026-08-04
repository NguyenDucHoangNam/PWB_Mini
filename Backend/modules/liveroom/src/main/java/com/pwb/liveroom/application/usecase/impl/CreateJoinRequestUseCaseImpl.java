package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.CreateJoinRequestCommand;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.CreateJoinRequestUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
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
public class CreateJoinRequestUseCaseImpl implements CreateJoinRequestUseCase {

    private final JoinRequestRepository joinRequestRepository;
    private final RoomLoader roomLoader;
    private final RoomMembers roomMembers;
    private final LiveroomEventPublisher eventPublisher;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public JoinRequestView execute(CreateJoinRequestCommand command) {
        LiveRoom room = roomLoader.require(command.roomId());
        if (!room.isActive()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }
        if (room.isOwnedBy(command.actor().userId())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.SELF_JOIN_NOT_ALLOWED);
        }



        var replay = joinRequestRepository.findByRoomIdAndUserIdAndIdempotencyKey(
                room.getId(), command.actor().userId(), command.idempotencyKey());
        if (replay.isPresent()) {
            RoomMember member = roomMembers.loadOrCreate(room.getId(), command.actor().userId());
            log.debug("Replayed join request: roomId={} userId={} requestId={}",
                    room.getId(), command.actor().userId(), replay.get().getId());
            return viewFactory.toView(replay.get(), member);
        }

        RoomMember member = roomMembers.loadOrCreate(room.getId(), command.actor().userId());
        Instant now = Instant.now();

        if (member.isServingKickCooldown(now)) {
            long minutes = Math.max(
                    1, Duration.between(now, member.getKickedCooldownUntil()).toMinutes() + 1);
            throw new LiveroomBusinessException(
                    LiveroomErrorCode.KICKED_COOLDOWN, Map.of("minutes", minutes));
        }


        if (member.isLockedOut()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.REQUEST_LOCKED);
        }
        if (member.wasApproved()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ALREADY_APPROVED);
        }
        if (joinRequestRepository.findPendingByRoomIdAndUserId(room.getId(), command.actor().userId()).isPresent()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.DUPLICATE_REQUEST);
        }

        JoinRequest created = joinRequestRepository.save(JoinRequest.raise(
                room.getId(),
                room.getCurrentCycleId(),
                command.actor().userId(),
                command.actor().email(),
                command.idempotencyKey()
        ));

        eventPublisher.broadcastToRoom(RoomEvents.joinRequestCreated(created));

        log.info("Join request raised: roomId={} userId={} requestId={}",
                room.getId(), command.actor().userId(), created.getId());
        return viewFactory.toView(created, member);
    }
}