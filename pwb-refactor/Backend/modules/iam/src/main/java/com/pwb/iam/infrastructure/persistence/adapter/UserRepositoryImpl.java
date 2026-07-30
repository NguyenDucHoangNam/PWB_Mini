package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        UserJpaEntity target;
        if (user.getUserId() != null) {
            target = userJpaRepository.findByIdAndDeletedFalse(user.getUserId())
                    .orElse(null);
            target = userMapper.toEntity(user, target);
        } else {
            target = userMapper.toEntity(user, null);
        }
        UserJpaEntity saved = userJpaRepository.save(target);
        return userMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findByIdAndDeletedFalse(id).map(userMapper::toDomain);
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
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return userJpaRepository.findByUsernameAndDeletedFalse(username)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByIdAndStatus(UUID id, UserStatus status) {
        return userJpaRepository.findByIdAndStatusAndDeletedFalse(id, status)
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
    public boolean existsByUsername(String username) {
        return username != null && userJpaRepository.existsByUsernameAndDeletedFalse(username);
    }

    @Override
    public List<User> findProvisionalUsersCreatedBefore(Instant cutoff) {
        return userJpaRepository.findProvisionalUsersCreatedBefore(cutoff).stream()
                .map(userMapper::toDomain)
                .toList();
    }
}
