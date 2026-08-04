package com.pwb.iam.application.command;

import java.util.UUID;

public record AdminBanUserCommand(
        UUID adminId,
        UUID targetUserId,
        String reason
) {
}