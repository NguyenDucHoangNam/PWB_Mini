package com.pwb.liveroom.application.command;

import java.util.UUID;


public record ModerateParticipantCommand(
        UUID actorId,
        UUID roomId,
        UUID targetUserId,
        String reason
) {
}