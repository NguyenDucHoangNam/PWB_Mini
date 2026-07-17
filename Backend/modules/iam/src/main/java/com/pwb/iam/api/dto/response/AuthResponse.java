package com.pwb.iam.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    public enum NextStep {
        NONE,
        COMPLETE_PROFILE
    }

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private long expiresIn;

    private UUID userId;

    private String email;

    private String status;

    private String role;

    private NextStep nextStep;
}
