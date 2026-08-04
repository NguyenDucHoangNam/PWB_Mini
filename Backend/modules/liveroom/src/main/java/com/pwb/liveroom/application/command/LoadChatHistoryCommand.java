package com.pwb.liveroom.application.command;

import java.util.UUID;


public record LoadChatHistoryCommand(
        UUID actorId,
        UUID roomId,
        UUID cursor,
        int size
) {
}