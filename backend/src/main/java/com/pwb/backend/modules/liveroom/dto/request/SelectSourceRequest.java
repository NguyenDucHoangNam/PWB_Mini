package com.pwb.backend.modules.liveroom.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectSourceRequest(
        @NotNull(message = "{validation.demoId.required}")
        UUID demoId) {
}