package com.pwb.iam.application.command;

import java.util.UUID;

public record LogoutCommand(
        UUID userId
) {
    public LogoutCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }
}
