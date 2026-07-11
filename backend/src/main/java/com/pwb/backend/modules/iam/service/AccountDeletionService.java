package com.pwb.backend.modules.iam.service;

import com.pwb.backend.modules.iam.dto.request.DeleteAccountRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;

import java.util.UUID;

public interface AccountDeletionService {

    UserProfileResponse requestDeletion(UUID userId, DeleteAccountRequest request);

    UserProfileResponse cancelDeletion(UUID userId);
}
