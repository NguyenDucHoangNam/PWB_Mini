package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.iam.domain.service.RefreshTokenManager;

public record LoginResult(
        TokenService.AccessToken accessToken,
        RefreshTokenManager.RefreshToken refreshToken
) {
}