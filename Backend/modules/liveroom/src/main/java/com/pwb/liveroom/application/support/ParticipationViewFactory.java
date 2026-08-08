package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.service.UserDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class ParticipationViewFactory {

    private final UserDirectoryPort userDirectory;

    public JoinRequestView toView(JoinRequest request, RoomMember member) {
        return toView(request, member, userDirectory.avatarUrlOf(request.getUserId()));
    }

    public JoinRequestView toView(JoinRequest request, RoomMember member, String avatarUrl) {
        int rejectCount = member == null ? 0 : member.getRejectCountByOwner();
        return new JoinRequestView(
                request.getId(),
                request.getRoomId(),
                request.getUserId(),
                request.getUserEmail(),
                avatarUrl,
                request.getState(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getDecidedAt(),
                request.getDecidedBy(),
                rejectCount,
                Math.max(RoomMember.REJECT_LIMIT - rejectCount, 0)
        );
    }

    public List<JoinRequestView> toViews(
            List<JoinRequest> requests,
            Function<JoinRequest, RoomMember> memberLookup
    ) {
        Map<UUID, String> avatars = userDirectory.avatarUrlsOf(
                requests.stream().map(JoinRequest::getUserId).toList());
        return requests.stream()
                .map(request -> toView(
                        request, memberLookup.apply(request), avatars.get(request.getUserId())))
                .toList();
    }

    public ParticipantView toView(Participant participant, boolean oldest, boolean newest) {
        return toView(participant, oldest, newest, userDirectory.avatarUrlOf(participant.getUserId()));
    }

    public ParticipantView toView(
            Participant participant,
            boolean oldest,
            boolean newest,
            String avatarUrl
    ) {
        return new ParticipantView(
                participant.getId(),
                participant.getUserId(),
                participant.getUserEmail(),
                avatarUrl,
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
        Map<UUID, String> avatars = userDirectory.avatarUrlsOf(
                inRoomOrderedByJoin.stream().map(Participant::getUserId).toList());
        int last = inRoomOrderedByJoin.size() - 1;
        return IntStream.rangeClosed(0, last)
                .mapToObj(i -> {
                    Participant participant = inRoomOrderedByJoin.get(i);
                    return toView(participant, i == 0, i == last, avatars.get(participant.getUserId()));
                })
                .toList();
    }
}