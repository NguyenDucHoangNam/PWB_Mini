package com.pwb.iam.application.command;

import java.util.UUID;

public record UpdateAvatarCommand(
        UUID userId,
        AvatarUpload file
) {

    public UpdateAvatarCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
    }
}
