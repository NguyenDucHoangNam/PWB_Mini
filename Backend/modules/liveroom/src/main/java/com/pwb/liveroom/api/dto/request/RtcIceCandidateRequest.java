package com.pwb.liveroom.api.dto.request;

import com.pwb.liveroom.application.command.RtcIceCandidate;

import java.util.UUID;

public record RtcIceCandidateRequest(
        UUID targetUserId,
        String candidate,
        String sdpMid,
        Integer sdpMLineIndex,
        String usernameFragment
) {

    public RtcIceCandidate toCandidate() {
        return new RtcIceCandidate(candidate, sdpMid, sdpMLineIndex, usernameFragment);
    }
}