package com.pwb.liveroom.application.command;

import java.util.UUID;


public record AddTrackCommentCommand(
        UUID actorId,
        UUID roomId,
        UUID songId,
        Double positionSeconds,
        String content
) {
}