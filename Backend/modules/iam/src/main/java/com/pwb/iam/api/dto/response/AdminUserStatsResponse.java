package com.pwb.iam.api.dto.response;

import com.pwb.iam.application.dto.AdminUserStatsView;

public record AdminUserStatsResponse(
        long totalUsers,
        long activeUsers,
        long bannedUsers,
        long pendingDeletionUsers,
        long pendingVerificationUsers,
        long userRoleCount,
        long proRoleCount,
        long adminRoleCount,
        long newUsersToday,
        long newUsersThisWeek,
        long newUsersThisMonth
) {

    public static AdminUserStatsResponse from(AdminUserStatsView view) {
        return new AdminUserStatsResponse(
                view.totalUsers(),
                view.activeUsers(),
                view.bannedUsers(),
                view.pendingDeletionUsers(),
                view.pendingVerificationUsers(),
                view.userRoleCount(),
                view.proRoleCount(),
                view.adminRoleCount(),
                view.newUsersToday(),
                view.newUsersThisWeek(),
                view.newUsersThisMonth()
        );
    }
}
