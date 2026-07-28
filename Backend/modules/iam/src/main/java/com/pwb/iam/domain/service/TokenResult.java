package com.pwb.iam.domain.service;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class TokenResult {

    public enum NextStep {
        NONE,
        COMPLETE_PROFILE
    }

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final UUID userId;
    private final String email;
    private final String username;
    private final String status;
    private final String role;
    private final NextStep nextStep;
}
