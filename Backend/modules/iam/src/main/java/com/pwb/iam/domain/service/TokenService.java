package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.User;

public interface TokenService {

    TokenResult buildAuthTokens(User user, TokenResult.NextStep nextStep);

    String extractJtiFromRefreshToken(String token);

    boolean validateRefreshToken(String token);
}
