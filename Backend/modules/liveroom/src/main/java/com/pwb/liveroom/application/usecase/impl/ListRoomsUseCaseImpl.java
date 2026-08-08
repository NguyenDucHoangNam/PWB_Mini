package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.ListRoomsUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.RoomStatus;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListRoomsUseCaseImpl implements ListRoomsUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional(readOnly = true)
    public Page<RoomView> execute(UUID ownerId, RoomStatus status, Pageable pageable) {
        Page<LiveRoom> rooms = (status == null)
                ? liveRoomRepository.findAllByOwnerId(ownerId, pageable)
                : liveRoomRepository.findAllByOwnerIdAndStatus(ownerId, status, pageable);
        return rooms.map(roomViewFactory::toView);
    }
}