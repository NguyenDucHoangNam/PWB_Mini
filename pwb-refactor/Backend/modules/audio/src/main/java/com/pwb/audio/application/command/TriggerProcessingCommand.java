package com.pwb.audio.application.command;

import java.util.UUID;

public record TriggerProcessingCommand(
        UUID songId
) {
}
