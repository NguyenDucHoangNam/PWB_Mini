package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenManagerService;

public record LoginResult(
        User user,
        TokenManagerService.AccessTokenInfo accessToken,
        TokenManagerService.RefreshTokenInfo refreshToken,
        AuthNextStep nextStep
) {

    public LoginResult(User user, TokenManagerService.AccessTokenInfo accessToken, TokenManagerService.RefreshTokenInfo refreshToken) {
        this(user, accessToken, refreshToken, AuthNextStep.NONE);
    }
}
