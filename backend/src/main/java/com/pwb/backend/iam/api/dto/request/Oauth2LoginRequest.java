package com.pwb.backend.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record Oauth2LoginRequest(
    @NotBlank String idToken
) {}
