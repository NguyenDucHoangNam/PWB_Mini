package com.pwb.iam.application.command;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record UpdateAvatarCommand(
        UUID userId,
        MultipartFile file
) {

    public UpdateAvatarCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be null or empty");
        }
    }
}
