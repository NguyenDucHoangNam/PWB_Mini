package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.core.model.User;
import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import com.pwb.iam.infrastructure.service.AuthSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSupportServiceImpl implements AuthSupportService {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthResponse buildAuthResponse(User user, AuthResponse.NextStep nextStep) {
        if (user.getEmail() == null || user.getRole() == null || user.getRole().getName() == null) {
            throw new IllegalStateException(
                    "User missing email or role: userId=" + user.getUserId());
        }
        String roleName = user.getRole().getName().name();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getEmail().value(), roleName);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .userId(user.getUserId())
                .email(user.getEmail().value())
                .username(user.getUsername())
                .status(user.getStatus().name())
                .role(roleName)
                .nextStep(nextStep)
                .build();
    }

    @Override
    public UUID extractUserIdFromRefreshToken(String refreshToken) {
        return jwtTokenProvider.extractUserIdFromRefreshToken(refreshToken);
    }

    @Override
    public boolean validateRefreshToken(String refreshToken) {
        return jwtTokenProvider.validateRefreshToken(refreshToken);
    }
}
