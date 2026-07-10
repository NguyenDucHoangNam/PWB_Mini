package com.pwb.backend.audio.internal.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DistributeDemoRequest(
    @NotBlank
    @Email(message = "recipientEmail must be a valid email address")
    String recipientEmail,

    @NotNull
    Boolean allowDownload
) {}
