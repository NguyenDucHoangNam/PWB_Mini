package com.pwb.backend.service;

import com.pwb.backend.dto.request.ResetPasswordRequest;
import com.pwb.backend.dto.response.AuthMessageResponse;

public interface ForgotPasswordService {

    AuthMessageResponse requestReset(String email);

    AuthMessageResponse resetPassword(ResetPasswordRequest request);
}