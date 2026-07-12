package com.pwb.backend.modules.audio.dto.response;

import com.pwb.backend.modules.audio.enums.DemoStatus;

import java.util.UUID;

public record ConfirmUploadResponse(
        UUID demoId,
        DemoStatus status) {
}