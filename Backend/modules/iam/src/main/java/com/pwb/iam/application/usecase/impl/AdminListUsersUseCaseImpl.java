package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.usecase.AdminListUsersUseCase;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.persistence.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminListUsersUseCaseImpl implements AdminListUsersUseCase {

    private final UserJpaRepository userJpaRepository;
    private final com.pwb.iam.infrastructure.persistence.mapper.UserMapper userMapper;

    @Override
    public Page<AdminUserView> execute(UUID adminId, UserSearchCriteria criteria, Pageable pageable) {
        Specification<UserJpaEntity> spec = UserSpecifications.fromCriteria(criteria);
        return userJpaRepository.findAll(spec, pageable).map(entity -> {
            var user = userMapper.toDomain(entity);
            return AdminUserView.from(user, entity.getCreatedAt(), entity.getUpdatedAt());
        });
    }
}