package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.request.PlaybackSyncCommand;
import com.pwb.backend.modules.liveroom.dto.response.PlaybackStateResponse;

import java.util.UUID;

public interface PlaybackSyncService {

    PlaybackStateResponse getPlaybackState(String roomCode);

    void handleSyncCommand(String roomCode, UUID userId, PlaybackSyncCommand command);
}