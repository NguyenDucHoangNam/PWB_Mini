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
public class AuthMessageResponse {

    private UUID userId;

    private String message;

    private Integer expiresIn;

    public static AuthMessageResponse of(UUID userId, String message) {
        return AuthMessageResponse.builder()
                .userId(userId)
                .message(message)
                .build();
    }

    public static AuthMessageResponse of(UUID userId, String message, Integer expiresIn) {
        return AuthMessageResponse.builder()
                .userId(userId)
                .message(message)
                .expiresIn(expiresIn)
                .build();
    }
}