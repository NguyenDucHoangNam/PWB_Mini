package com.pwb.backend.modules.iam.dto.response;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String fullName,
        String role,
        String status,
        String avatarUrl) {
}
