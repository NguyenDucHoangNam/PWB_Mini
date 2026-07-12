package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.dto.response.JoinRoomResponse;

public interface ListenerJoinService {

    JoinRoomResponse joinRoom(String roomCode, String displayName);
}