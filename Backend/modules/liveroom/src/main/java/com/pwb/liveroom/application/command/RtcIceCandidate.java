package com.pwb.liveroom.application.command;

public record RtcIceCandidate(
        String candidate,
        String sdpMid,
        Integer sdpMLineIndex,
        String usernameFragment
) {
}