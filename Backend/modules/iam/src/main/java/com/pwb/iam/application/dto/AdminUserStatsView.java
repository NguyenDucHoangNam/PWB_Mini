package com.pwb.iam.application.dto;

public record AdminUserStatsView(
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
}