package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.Admissions;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.ApproveJoinRequestUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
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
public class ApproveJoinRequestUseCaseImpl implements ApproveJoinRequestUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final Admissions admissions;
    private final RoomMembers roomMembers;
    private final Playbacks playbacks;
    private final LiveroomEventPublisher eventPublisher;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional
    public JoinRequestView execute(UUID actorId, UUID roomId, UUID requestId) {
        LiveRoom room = liveRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));
        if (!room.isOwnedBy(actorId)) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }
        if (!room.isActive()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        JoinRequest request = joinRequestRepository.findById(requestId)
                .filter(r -> r.getRoomId().equals(roomId))
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.REQUEST_EXPIRED);
        }

        Instant now = Instant.now();
        RoomMember member = roomMembers.loadOrCreate(roomId, request.getUserId());




        if (!room.hasFreeSlot()) {
            request.rejectByCapacity(actorId, now);
            JoinRequest settled = joinRequestRepository.save(request);
            member.recordCapacityRejection();
            roomMembers.save(member);
            eventPublisher.sendToUser(settled.getUserId(),
                    RoomEvents.requestRejectedByCapacity(settled, member.getRejectCountByCapacity()));
            log.info("Join request rejected because the room was full: roomId={} requestId={}",
                    roomId, requestId);
            return viewFactory.toView(settled, member);
        }

        var participant = admissions.admit(
                room, request.getUserId(), request.getUserEmail(), ParticipantRole.PARTICIPANT, now);
        liveRoomRepository.save(room);

        request.approve(actorId, now);
        JoinRequest approved = joinRequestRepository.save(request);

        eventPublisher.sendToUser(approved.getUserId(), RoomEvents.requestApproved(approved));



        playbacks.sendCurrentTo(approved.getUserId(), room, now);
        eventPublisher.broadcastToRoom(RoomEvents.participantJoined(room, participant));
        eventPublisher.broadcastToRoom(RoomEvents.capacityChanged(room));
        if (!room.hasFreeSlot()) {
            eventPublisher.broadcastToRoom(RoomEvents.capacityReached(room));
        }

        log.info("Join request approved and user admitted: roomId={} userId={} requestId={} count={}/{}",
                roomId, request.getUserId(), requestId,
                room.getCurrentParticipantCount(), room.effectiveMaxParticipants());
        return viewFactory.toView(approved, roomMembers.loadOrCreate(roomId, request.getUserId()));
    }
}