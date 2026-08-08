package com.pwb.liveroom.application.command;

import java.util.UUID;


public record UpdateMediaStateCommand(
        UUID actorId,
        UUID roomId,
        Boolean cameraOn,
        Boolean micOn
) {
}