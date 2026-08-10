package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.dto.AdminUserSuggestionView;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.usecase.AdminSearchUsersUseCase;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.persistence.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSearchUsersUseCaseImpl implements AdminSearchUsersUseCase {

    private static final int MAX_SUGGESTIONS = 20;

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public Page<AdminUserView> search(UUID adminId, UserSearchCriteria criteria, Pageable pageable) {
        return userJpaRepository
                .findAll(UserSpecifications.fromCriteria(criteria), pageable)
                .map(this::toView);
    }

    @Override
    public List<AdminUserSuggestionView> suggest(UUID adminId, UserSearchCriteria criteria, int limit) {
        if (criteria.keyword() == null || criteria.keyword().isBlank()) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);

        return userJpaRepository
                .findAll(UserSpecifications.fromCriteria(criteria), PageRequest.of(0, capped))
                .getContent().stream()
                .map(entity -> new AdminUserSuggestionView(
                        entity.getId(), entity.getEmail(), entity.getFullName()))
                .toList();
    }

    private AdminUserView toView(UserJpaEntity entity) {
        return AdminUserView.from(
                userMapper.toDomain(entity), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
