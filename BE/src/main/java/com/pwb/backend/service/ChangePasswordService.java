package com.pwb.backend.service;

import com.pwb.backend.dto.request.ChangePasswordRequest;
import com.pwb.backend.dto.response.AuthMessageResponse;

import java.util.UUID;

public interface ChangePasswordService {

    AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request);
}