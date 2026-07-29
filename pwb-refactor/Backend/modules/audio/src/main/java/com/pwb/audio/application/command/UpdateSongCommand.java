package com.pwb.audio.application.command;

import java.util.UUID;

public record UpdateSongCommand(
        UUID userId,
        UUID songId,
        String title,
        String artist,
        String album
) {
}
