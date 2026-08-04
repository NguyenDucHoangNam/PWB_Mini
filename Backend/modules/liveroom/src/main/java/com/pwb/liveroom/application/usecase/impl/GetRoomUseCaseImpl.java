package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.GetRoomUseCase;
import com.pwb.liveroom.application.view.RoomView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class GetRoomUseCaseImpl implements GetRoomUseCase {

    private final RoomLoader roomLoader;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional(readOnly = true)
    public RoomView execute(UUID actorId, UUID roomId) {
        return roomViewFactory.toView(roomLoader.requireOwned(roomId, actorId));
    }
}