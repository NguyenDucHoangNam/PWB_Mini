package com.pwb.backend.modules.iam.service;

public interface GoogleOAuthService {

    GoogleUserInfo verify(String idToken);
}
