package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class Admissions {

    private final ParticipantRepository participantRepository;
    private final RoomMembers roomMembers;


    public Participant admit(
            LiveRoom room,
            UUID userId,
            String userEmail,
            ParticipantRole roomRole,
            Instant now
    ) {
        UUID cycleId = requireCycle(room);

        Participant participant = participantRepository.findByCycleIdAndUserId(cycleId, userId)
                .orElse(null);
        if (participant != null && participant.isInRoom()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ALREADY_IN_ROOM);
        }
        if (!room.hasFreeSlot()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_FULL);
        }

        room.admitParticipant();

        Participant seated;
        if (participant == null) {
            seated = participantRepository.save(
                    Participant.join(room.getId(), cycleId, userId, userEmail, roomRole, now));
        } else {
            participant.rejoin(now);
            seated = participantRepository.save(participant);
        }

        RoomMember member = roomMembers.loadOrCreate(room.getId(), userId);
        member.markApproved();
        roomMembers.save(member);

        return seated;
    }

    private UUID requireCycle(LiveRoom room) {
        UUID cycleId = room.getCurrentCycleId();
        if (cycleId == null) {
            throw new IllegalStateException("Active room has no session cycle: " + room.getId());
        }
        return cycleId;
    }
}