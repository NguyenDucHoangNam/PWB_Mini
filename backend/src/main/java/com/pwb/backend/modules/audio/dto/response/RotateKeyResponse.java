package com.pwb.backend.modules.audio.dto.response;

import java.util.UUID;

public record RotateKeyResponse(
        UUID demoId,
        int newVersion,
        boolean rotated,
        String messageKey) {
}