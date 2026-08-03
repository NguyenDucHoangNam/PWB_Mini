package com.pwb.audio.application.command;

import java.util.UUID;

public record UpdateVoiceTagCommand(
        UUID userId,
        UUID voiceTagId,
        String name
) {
}
