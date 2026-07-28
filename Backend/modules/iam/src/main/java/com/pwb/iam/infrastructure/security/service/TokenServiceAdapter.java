package com.pwb.iam.infrastructure.security.service;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenServiceAdapter implements TokenService {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public TokenResult buildAuthTokens(User user, TokenResult.NextStep nextStep) {
        if (user.getEmail() == null || user.getRole() == null || user.getRole().getName() == null) {
            throw new IllegalStateException(
                    "User missing email or role: userId=" + user.getUserId());
        }
        String roleName = user.getRole().getName().name();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getEmail().value(), roleName);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        return TokenResult.builder()
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
    public String extractJtiFromRefreshToken(String token) {
        return jwtTokenProvider.extractJtiFromRefreshToken(token);
    }

    @Override
    public boolean validateRefreshToken(String token) {
        return jwtTokenProvider.validateRefreshToken(token);
    }
}
