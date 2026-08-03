package com.pwb.audio.application.command;

import java.util.UUID;

public record DeleteSongCommand(
        UUID userId,
        UUID songId
) {
}
