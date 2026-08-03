package com.pwb.audio.application.command;

import java.util.UUID;

public record DeleteVoiceTagCommand(
        UUID userId,
        UUID voiceTagId
) {
}
