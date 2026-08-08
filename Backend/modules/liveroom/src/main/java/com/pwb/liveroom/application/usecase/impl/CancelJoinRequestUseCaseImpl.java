package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.CancelJoinRequestUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.model.JoinRequest;
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
public class CancelJoinRequestUseCaseImpl implements CancelJoinRequestUseCase {

    private final JoinRequestRepository joinRequestRepository;
    private final RoomMembers roomMembers;
    private final LiveroomEventPublisher eventPublisher;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public JoinRequestView execute(UUID actorId, UUID roomId, UUID requestId) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .filter(r -> r.getRoomId().equals(roomId))
                .filter(r -> r.getUserId().equals(actorId))
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.REQUEST_NOT_FOUND));

        if (!request.isPending()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.REQUEST_EXPIRED);
        }

        request.cancel(Instant.now());
        JoinRequest cancelled = joinRequestRepository.save(request);

        eventPublisher.broadcastToRoom(RoomEvents.joinRequestCancelled(cancelled));

        log.info("Join request cancelled by user: roomId={} userId={} requestId={}",
                roomId, actorId, requestId);
        return viewFactory.toView(cancelled, roomMembers.loadOrCreate(roomId, actorId));
    }
}