package com.pwb.liveroom.application.command;

import java.util.UUID;

public record SelectSongCommand(
        UUID actorId,
        UUID roomId,
        UUID songId
) {
}