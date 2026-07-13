package com.pwb.backend.modules.liveroom.service;

import java.util.UUID;

public interface PlaybackService {

    void selectSource(String roomCode, UUID hostId, UUID demoId);
}
