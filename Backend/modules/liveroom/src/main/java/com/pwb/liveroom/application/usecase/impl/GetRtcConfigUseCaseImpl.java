package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.RoomSessions;
import com.pwb.liveroom.application.support.TurnCredentialFactory;
import com.pwb.liveroom.application.usecase.GetRtcConfigUseCase;
import com.pwb.liveroom.application.view.RtcConfigView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetRtcConfigUseCaseImpl implements GetRtcConfigUseCase {

    private final RoomSessions roomSessions;
    private final TurnCredentialFactory turnCredentialFactory;
    private final LiveroomConfig config;

    @Override
    @Transactional(readOnly = true)
    public RtcConfigView execute(UUID actorId, UUID roomId) {
        LiveRoom room = roomSessions.requireActiveRoom(roomId);
        roomSessions.requireInRoom(room, actorId);

        List<RtcConfigView.IceServerView> iceServers = new ArrayList<>(
                config.getRtc().getIceServers().stream()
                        .map(server -> new RtcConfigView.IceServerView(
                                List.copyOf(server.getUrls()), server.getUsername(), server.getCredential()))
                        .toList());

        turnCredentialFactory.create(actorId)
                .map(turn -> new RtcConfigView.IceServerView(
                        turn.urls(), turn.username(), turn.credential()))
                .ifPresent(iceServers::add);

        return new RtcConfigView(
                List.copyOf(iceServers),
                room.effectiveMaxParticipants() - 1,
                config.getRtc().getMaxSdpLength(),
                config.getRtc().getMaxCandidateLength()
        );
    }
}