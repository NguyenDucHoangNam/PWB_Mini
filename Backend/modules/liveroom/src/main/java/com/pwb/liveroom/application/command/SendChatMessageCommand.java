package com.pwb.liveroom.application.command;

import java.util.UUID;


public record SendChatMessageCommand(
        UUID actorId,
        UUID roomId,
        String content
) {
}