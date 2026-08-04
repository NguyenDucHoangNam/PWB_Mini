package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.RtcConfigView;

import java.util.List;

public record RtcConfigResponse(
        List<IceServerResponse> iceServers,
        int maxMeshPeers,
        int maxSdpLength,
        int maxCandidateLength
) {

    public record IceServerResponse(List<String> urls, String username, String credential) {
    }

    public static RtcConfigResponse from(RtcConfigView view) {
        return new RtcConfigResponse(
                view.iceServers().stream()
                        .map(server -> new IceServerResponse(
                                server.urls(), server.username(), server.credential()))
                        .toList(),
                view.maxMeshPeers(),
                view.maxSdpLength(),
                view.maxCandidateLength()
        );
    }
}