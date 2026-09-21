package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.dto.AdminUserStatsView;
import com.pwb.iam.application.usecase.AdminUserStatsUseCase;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserStatsUseCaseImpl implements AdminUserStatsUseCase {

    private final UserRepository userRepository;

    @Override
    public AdminUserStatsView execute() {
        long totalUsers = userRepository.countAll();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long bannedUsers = userRepository.countByStatus(UserStatus.BANNED);
        long pendingDeletionUsers = userRepository.countByStatus(UserStatus.PENDING_DELETION);
        long pendingVerificationUsers = userRepository.countByStatus(UserStatus.PENDING_VERIFICATION);

        long userRoleCount = userRepository.countByRole(RoleName.USER);
        long adminRoleCount = userRepository.countByRole(RoleName.ADMIN);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfWeek = today.with(ChronoField.DAY_OF_WEEK, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long newUsersToday = userRepository.countCreatedAfter(startOfToday);
        long newUsersThisWeek = userRepository.countCreatedAfter(startOfWeek);
        long newUsersThisMonth = userRepository.countCreatedAfter(startOfMonth);

        return new AdminUserStatsView(
                totalUsers,
                activeUsers,
                bannedUsers,
                pendingDeletionUsers,
                pendingVerificationUsers,
                userRoleCount,
                adminRoleCount,
                newUsersToday,
                newUsersThisWeek,
                newUsersThisMonth
        );
    }
}
