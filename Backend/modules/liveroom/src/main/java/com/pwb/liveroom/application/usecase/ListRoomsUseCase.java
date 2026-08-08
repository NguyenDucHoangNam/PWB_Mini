package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ListRoomsUseCase {


    Page<RoomView> execute(UUID ownerId, RoomStatus status, Pageable pageable);
}