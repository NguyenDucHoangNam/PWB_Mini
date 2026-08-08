package com.pwb.liveroom.application.command;

import java.util.UUID;

public record RelayRtcSignalCommand(
        UUID actorId,
        UUID roomId,
        UUID targetUserId,
        RtcSignalType type,
        String sdp,
        RtcIceCandidate candidate
) {

    public static RelayRtcSignalCommand description(
            UUID actorId, UUID roomId, UUID targetUserId, RtcSignalType type, String sdp) {
        return new RelayRtcSignalCommand(actorId, roomId, targetUserId, type, sdp, null);
    }

    public static RelayRtcSignalCommand iceCandidate(
            UUID actorId, UUID roomId, UUID targetUserId, RtcIceCandidate candidate) {
        return new RelayRtcSignalCommand(
                actorId, roomId, targetUserId, RtcSignalType.ICE_CANDIDATE, null, candidate);
    }
}