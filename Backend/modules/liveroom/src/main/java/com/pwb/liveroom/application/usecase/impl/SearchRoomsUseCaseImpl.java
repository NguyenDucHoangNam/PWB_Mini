package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.SearchRoomsUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.RoomSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchRoomsUseCaseImpl implements SearchRoomsUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional(readOnly = true)
    public Page<RoomView> execute(RoomSearchCriteria criteria, Pageable pageable) {
        Page<LiveRoom> page = liveRoomRepository.search(criteria, pageable);
        return page.map(roomViewFactory::toView);
    }
}
