package com.pwb.backend.modules.share.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DistributeDemoRequest(

        @NotBlank(message = "{validation.distribution.recipientEmail.required}")
        @Email(message = "{validation.distribution.recipientEmail.format}")
        @Size(max = 100, message = "{validation.distribution.recipientEmail.length}")
        String recipientEmail,

        @NotNull(message = "{validation.distribution.allowDownload.required}")
        Boolean allowDownload
) {
}