package com.pwb.backend.modules.iam.service;

import java.util.UUID;

public interface PasswordChangeService {

    void requestPasswordReset(String email);

    void resetPassword(String token, String newPassword);

    int changePassword(UUID userId, String oldPassword, String newPassword, String currentRefreshToken);
}