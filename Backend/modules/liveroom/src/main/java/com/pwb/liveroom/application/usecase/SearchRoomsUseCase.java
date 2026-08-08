package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.repository.RoomSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchRoomsUseCase {


    Page<RoomView> execute(RoomSearchCriteria criteria, Pageable pageable);
}