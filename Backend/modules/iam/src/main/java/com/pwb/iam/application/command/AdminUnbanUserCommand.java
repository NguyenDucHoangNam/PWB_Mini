package com.pwb.iam.application.command;

import java.util.UUID;

public record AdminUnbanUserCommand(
        UUID adminId,
        UUID targetUserId
) {
}