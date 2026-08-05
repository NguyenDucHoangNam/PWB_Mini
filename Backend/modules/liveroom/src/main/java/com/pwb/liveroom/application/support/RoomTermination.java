package com.pwb.liveroom.application.support;

import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import com.pwb.liveroom.domain.service.TrackCommentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class RoomTermination {

    private final LiveRoomRepository liveRoomRepository;
    private final ParticipantRepository participantRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final SessionCycleStarter sessionCycleStarter;
    private final Playbacks playbacks;
    private final TrackCommentStore trackCommentStore;


    public LiveRoom terminate(LiveRoom room, EndedReason reason, Instant at) {
        UUID cycleId = room.getCurrentCycleId();

        closeOccupants(cycleId, at);
        expirePendingRequests(room.getId(), at);



        playbacks.freezeOnRoomEnd(room, at);
        trackCommentStore.clearCycle(cycleId);

        room.end(reason, at);
        sessionCycleStarter.close(room, reason, at);
        return liveRoomRepository.save(room);
    }

    private void closeOccupants(UUID cycleId, Instant at) {
        if (cycleId == null) {
            return;
        }
        List<Participant> occupants = participantRepository.findInRoomByCycleId(cycleId);
        occupants.forEach(participant -> participant.closeWithRoom(at));
        participantRepository.saveAll(occupants);
    }


    private void expirePendingRequests(UUID roomId, Instant at) {
        List<JoinRequest> pending = joinRequestRepository.findPendingByRoomId(roomId);
        pending.forEach(request -> request.expire(at));
        joinRequestRepository.saveAll(pending);
    }
}