package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "{validation.token.required}") String idToken,
        String nonce,
        String captchaToken) {
}
