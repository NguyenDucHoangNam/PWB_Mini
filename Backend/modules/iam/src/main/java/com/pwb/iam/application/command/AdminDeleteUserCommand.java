package com.pwb.iam.application.command;

import java.util.UUID;

public record AdminDeleteUserCommand(
        UUID adminId,
        UUID targetUserId,
        String reason
) {
}