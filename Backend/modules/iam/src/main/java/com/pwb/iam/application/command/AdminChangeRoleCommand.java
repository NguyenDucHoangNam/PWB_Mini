package com.pwb.iam.application.command;

import com.pwb.iam.domain.model.RoleName;

import java.util.UUID;

public record AdminChangeRoleCommand(
        UUID adminId,
        UUID targetUserId,
        RoleName newRole
) {
}