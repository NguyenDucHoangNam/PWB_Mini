package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.support.RoomMembers;
import com.pwb.liveroom.application.usecase.ListPendingJoinRequestsUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListPendingJoinRequestsUseCaseImpl implements ListPendingJoinRequestsUseCase {

    private final JoinRequestRepository joinRequestRepository;
    private final RoomLoader roomLoader;
    private final RoomMembers roomMembers;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequestView> execute(UUID actorId, UUID roomId) {
        roomLoader.requireOwned(roomId, actorId);
        return viewFactory.toViews(
                joinRequestRepository.findPendingByRoomId(roomId),
                request -> roomMembers.loadOrCreate(roomId, request.getUserId()));
    }
}