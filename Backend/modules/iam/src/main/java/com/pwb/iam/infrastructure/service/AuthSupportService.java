package com.pwb.iam.infrastructure.service;

import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.core.model.User;

import java.util.UUID;

public interface AuthSupportService {

    AuthResponse buildAuthResponse(User user, AuthResponse.NextStep nextStep);

    UUID extractUserIdFromRefreshToken(String refreshToken);

    boolean validateRefreshToken(String refreshToken);
}
