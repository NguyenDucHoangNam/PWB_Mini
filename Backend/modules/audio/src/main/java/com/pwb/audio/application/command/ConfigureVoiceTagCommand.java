package com.pwb.audio.application.command;

import java.util.UUID;

public record ConfigureVoiceTagCommand(
        UUID userId,
        UUID songId,
        VoiceTagSettings settings
) {
}
