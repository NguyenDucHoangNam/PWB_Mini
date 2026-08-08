package com.pwb.iam.application.usecase;

import com.pwb.iam.application.dto.ProfileView;

import java.util.UUID;

public interface GetProfileUseCase {

    ProfileView execute(UUID userId);
}
