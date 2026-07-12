package com.pwb.backend.modules.iam.service;

public interface GoogleOAuthService {

    GoogleUserInfo verify(String idToken);

    GoogleUserInfo verify(String idToken, String expectedNonce);
}
