package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.RoomSessions;
import com.pwb.liveroom.application.usecase.GetRtcConfigUseCase;
import com.pwb.liveroom.application.view.RtcConfigView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetRtcConfigUseCaseImpl implements GetRtcConfigUseCase {

    private final RoomSessions roomSessions;
    private final LiveroomConfig config;

    @Override
    @Transactional(readOnly = true)
    public RtcConfigView execute(UUID actorId, UUID roomId) {
        LiveRoom room = roomSessions.requireActiveRoom(roomId);
        roomSessions.requireInRoom(room, actorId);

        List<RtcConfigView.IceServerView> iceServers = config.getRtc().getIceServers().stream()
                .map(server -> new RtcConfigView.IceServerView(
                        List.copyOf(server.getUrls()), server.getUsername(), server.getCredential()))
                .toList();

        return new RtcConfigView(
                iceServers,
                room.effectiveMaxParticipants() - 1,
                config.getRtc().getMaxSdpLength(),
                config.getRtc().getMaxCandidateLength()
        );
    }
}
