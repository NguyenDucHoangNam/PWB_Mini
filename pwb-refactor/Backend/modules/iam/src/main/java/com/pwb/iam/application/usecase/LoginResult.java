package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.domain.service.TokenService;

public record LoginResult(
        User user,
        TokenService.AccessToken accessToken,
        RefreshTokenManager.RefreshToken refreshToken
) {
}