package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.domain.enums.EndedReason;

import java.util.UUID;

public interface AutoEndRoomUseCase {


    boolean execute(UUID roomId, EndedReason reason);
}