package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.view.RtcConfigView;

import java.util.UUID;

public interface GetRtcConfigUseCase {

    RtcConfigView execute(UUID actorId, UUID roomId);
}