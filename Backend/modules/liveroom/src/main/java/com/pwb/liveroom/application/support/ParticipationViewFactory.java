package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.RoomMember;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParticipationViewFactory {

    public JoinRequestView toView(JoinRequest request, RoomMember member) {
        int rejectCount = member == null ? 0 : member.getRejectCountByOwner();
        return new JoinRequestView(
                request.getId(),
                request.getRoomId(),
                request.getUserId(),
                request.getUserEmail(),
                request.getState(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getDecidedAt(),
                request.getDecidedBy(),
                rejectCount,
                Math.max(RoomMember.REJECT_LIMIT - rejectCount, 0)
        );
    }

    public ParticipantView toView(Participant participant, boolean oldest, boolean newest) {
        return new ParticipantView(
                participant.getId(),
                participant.getUserId(),
                participant.getUserEmail(),
                participant.getRoomRole(),
                participant.getState(),
                participant.getJoinedAt(),
                participant.getLeftAt(),
                participant.isCameraOn(),
                participant.isMicOn(),
                participant.getMicState(),
                participant.getMicUnmuteCooldownUntil(),
                participant.getLastInteractionAt(),
                oldest,
                newest
        );
    }


    public List<ParticipantView> toRankedViews(List<Participant> inRoomOrderedByJoin) {
        int last = inRoomOrderedByJoin.size() - 1;
        return java.util.stream.IntStream.rangeClosed(0, last)
                .mapToObj(i -> toView(inRoomOrderedByJoin.get(i), i == 0, i == last))
                .toList();
    }
}