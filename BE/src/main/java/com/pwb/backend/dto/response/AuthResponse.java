package com.pwb.backend.dto.response;

import com.pwb.backend.enums.UserStatus;
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

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private long expiresIn;

    private UUID userId;

    private String email;

    private UserStatus status;

    private String role;

    private NextStep nextStep;

    public enum NextStep {
        NONE,
        COMPLETE_PROFILE
    }
}