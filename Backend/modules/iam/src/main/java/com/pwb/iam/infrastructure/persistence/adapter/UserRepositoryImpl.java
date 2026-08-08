package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.persistence.specification.UserSpecifications;
import com.pwb.iam.infrastructure.search.IamSearchIndexWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;
    private final IamSearchIndexWriter searchIndexWriter;

    /**
     * The index is refreshed from the persisted entity rather than the domain aggregate, because
     * {@code User.rehydrate} drops the audit timestamps and the index sorts on them.
     */
    @Override
    public User save(User user) {
        UserJpaEntity target = userJpaRepository.findByIdAndDeletedFalse(user.getUserId())
                .map(existing -> userMapper.toEntity(user, existing))
                .orElseGet(() -> userMapper.toEntity(user, null));

        UserJpaEntity saved = userJpaRepository.save(target);
        searchIndexWriter.userSaved(saved);
        return userMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findByIdAndDeletedFalse(id).map(userMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllById(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userJpaRepository.findByIdInAndDeletedFalse(ids).stream()
                .map(userMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return userJpaRepository.findByEmailAndDeletedFalse(email.toLowerCase())
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByOAuthProviderAndOAuthId(OAuthProvider provider, String oauthId) {
        return userJpaRepository.findByOauthProviderAndOauthIdAndDeletedFalse(provider, oauthId)
                .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && userJpaRepository.existsByEmailAndDeletedFalse(email.toLowerCase());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> findAll(UserSearchCriteria criteria, Pageable pageable) {
        Specification<UserJpaEntity> spec = UserSpecifications.fromCriteria(criteria);
        return userJpaRepository.findAll(spec, pageable).map(userMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(UserStatus status) {
        return userJpaRepository.countByStatusAndDeletedFalse(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRole(RoleName role) {
        return userJpaRepository.countByRole_NameAndDeletedFalse(role.name());
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return userJpaRepository.countByDeletedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCreatedAfter(Instant after) {
        return userJpaRepository.countByCreatedAtAfterAndDeletedFalse(after);
    }
}
