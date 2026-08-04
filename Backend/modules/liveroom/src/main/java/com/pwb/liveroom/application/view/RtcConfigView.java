package com.pwb.liveroom.application.view;

import java.util.List;

public record RtcConfigView(
        List<IceServerView> iceServers,
        int maxMeshPeers,
        int maxSdpLength,
        int maxCandidateLength
) {

    public record IceServerView(List<String> urls, String username, String credential) {
    }
}
