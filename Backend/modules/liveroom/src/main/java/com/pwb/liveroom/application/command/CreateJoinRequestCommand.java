package com.pwb.liveroom.application.command;

import java.util.UUID;


public record CreateJoinRequestCommand(
        Actor actor,
        UUID roomId,
        String idempotencyKey
) {
}