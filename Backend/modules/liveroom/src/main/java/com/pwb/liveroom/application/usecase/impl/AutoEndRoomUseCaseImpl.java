package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.support.OwnerPresenceAnnouncer;
import com.pwb.liveroom.application.support.RoomTermination;
import com.pwb.liveroom.application.usecase.AutoEndRoomUseCase;
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
public class AutoEndRoomUseCaseImpl implements AutoEndRoomUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomTermination roomTermination;
    private final LiveroomEventPublisher eventPublisher;
    private final OwnerPresenceAnnouncer ownerPresenceAnnouncer;

    @Override
    @Transactional
    public boolean execute(UUID roomId, EndedReason reason) {
        LiveRoom room = liveRoomRepository.findByIdForUpdate(roomId).orElse(null);
        if (room == null || !room.isActive() || !stillQualifies(room, reason, Instant.now())) {
            return false;
        }

        LiveRoom ended = roomTermination.terminate(room, reason, Instant.now());

        ownerPresenceAnnouncer.forget(roomId);
        eventPublisher.broadcastToRoom(RoomEvents.roomAutoEnded(ended, reason));

        log.info("Live room auto-ended: roomId={} ownerId={} reason={}",
                ended.getId(), ended.getOwnerId(), reason);
        return true;
    }

    private boolean stillQualifies(LiveRoom room, EndedReason reason, Instant now) {
        return switch (reason) {
            case OWNER_GRACE_EXPIRED -> room.isOwnerAbsent()
                    && room.ownerGraceExpiresAt() != null
                    && now.isAfter(room.ownerGraceExpiresAt());


            case EMPTY_TIMEOUT -> room.getCurrentParticipantCount() == 0 && !room.isOwnerAbsent();
            default -> false;
        };
    }
}