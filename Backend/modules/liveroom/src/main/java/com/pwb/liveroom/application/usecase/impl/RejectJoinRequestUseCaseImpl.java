package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.RejectJoinRequestUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class RejectJoinRequestUseCaseImpl implements RejectJoinRequestUseCase {

    private final JoinRequestRepository joinRequestRepository;
    private final RoomLoader roomLoader;
    private final RoomMembers roomMembers;
    private final LiveroomEventPublisher eventPublisher;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public JoinRequestView execute(UUID actorId, UUID roomId, UUID requestId) {
        roomLoader.requireOwned(roomId, actorId);

        JoinRequest request = joinRequestRepository.findById(requestId)
                .filter(r -> r.getRoomId().equals(roomId))
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.REQUEST_EXPIRED);
        }

        request.rejectByOwner(actorId, Instant.now());
        JoinRequest rejected = joinRequestRepository.save(request);

        RoomMember member = roomMembers.loadOrCreate(roomId, request.getUserId());
        member.recordOwnerRejection();
        roomMembers.save(member);

        int attemptsRemaining = Math.max(RoomMember.REJECT_LIMIT - member.getRejectCountByOwner(), 0);
        eventPublisher.sendToUser(rejected.getUserId(), RoomEvents.requestRejectedByOwner(
                rejected, member.getRejectCountByOwner(), attemptsRemaining));
        if (member.isLockedOut()) {
            eventPublisher.sendToUser(rejected.getUserId(), RoomEvents.requestLocked(
                    roomId, rejected.getUserId(), member.getRejectCountByOwner()));
        }

        log.info("Join request rejected by owner: roomId={} userId={} requestId={} rejectCount={} lockedOut={}",
                roomId, request.getUserId(), requestId, member.getRejectCountByOwner(), member.isLockedOut());
        return viewFactory.toView(rejected, member);
    }
}