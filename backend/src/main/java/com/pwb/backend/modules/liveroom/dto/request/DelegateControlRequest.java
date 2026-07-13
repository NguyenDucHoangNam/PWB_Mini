package com.pwb.backend.modules.liveroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record DelegateControlRequest(
        @NotNull UUID listenerId,
        @NotBlank
        @Pattern(regexp = "^(GRANT|REVOKE)$", message = "{validation.delegation.action}")
        String action) {
}