package com.pwb.liveroom.application.support;

import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class RoomSubscriptionPolicy {

    private final LiveRoomRepository liveRoomRepository;
    private final ParticipantRepository participantRepository;
    private final JoinRequestRepository joinRequestRepository;

    @Transactional(readOnly = true)
    public boolean canSubscribe(UUID userId, UUID roomId) {
        LiveRoom room = liveRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return false;
        }


        if (room.isOwnedBy(userId)) {
            return true;
        }
        if (room.getCurrentCycleId() != null
                && participantRepository.findByCycleIdAndUserId(room.getCurrentCycleId(), userId)
                        .filter(Participant::isInRoom)
                        .isPresent()) {
            return true;
        }
        return joinRequestRepository.findPendingByRoomIdAndUserId(roomId, userId)
                .filter(JoinRequest::isPending)
                .isPresent();
    }
}