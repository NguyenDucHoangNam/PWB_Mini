package com.pwb.iam.application.usecase;

import com.pwb.iam.application.dto.AdminUserView;

import java.util.UUID;

public interface AdminGetUserDetailUseCase {

    AdminUserView execute(UUID adminId, UUID targetUserId);
}