package com.pwb.backend.modules.iam.service;

import com.pwb.backend.modules.iam.dto.request.UpdateProfileRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;

import java.util.UUID;

public interface ProfileService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);
}
