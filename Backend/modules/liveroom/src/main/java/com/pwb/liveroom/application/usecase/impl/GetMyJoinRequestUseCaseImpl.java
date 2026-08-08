package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.usecase.GetMyJoinRequestUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import com.pwb.liveroom.domain.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetMyJoinRequestUseCaseImpl implements GetMyJoinRequestUseCase {

    private final JoinRequestRepository joinRequestRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional(readOnly = true)
    public JoinRequestView execute(UUID actorId, UUID roomId) {
        JoinRequest request = joinRequestRepository.findLatestByRoomIdAndUserId(roomId, actorId)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.REQUEST_NOT_FOUND));

        return viewFactory.toView(
                request,
                roomMemberRepository.findByRoomIdAndUserId(roomId, actorId).orElse(null));
    }
}