package com.pwb.backend.service;

import com.pwb.backend.dto.request.GoogleLoginRequest;
import com.pwb.backend.dto.response.AuthResponse;

public interface GoogleAuthService {

    AuthResponse loginWithGoogle(GoogleLoginRequest request);
}