package com.pwb.iam.api;

import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.response.AuthResponse;

public interface IamFacade {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
