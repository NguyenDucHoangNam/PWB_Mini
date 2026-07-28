package com.pwb.iam.domain.service;

import com.pwb.iam.domain.model.User;

public interface TokenService {

    String issueAccessToken(User user);

    long accessTokenExpiresInSeconds();
}
