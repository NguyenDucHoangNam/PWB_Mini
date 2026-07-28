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

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        UserJpaEntity entity;
        if (user.getUserId() != null) {
            Optional<UserJpaEntity> existingOpt = userJpaRepository.findById(user.getUserId());
            if (existingOpt.isPresent()) {
                entity = userMapper.toEntity(user, existingOpt.get());
            } else {
                entity = userMapper.toEntity(user);
            }
        } else {
            entity = userMapper.toEntity(user);
        }
        UserJpaEntity savedEntity = userJpaRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findByIdAndDeletedFalse(id)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmailAndDeletedFalse(email)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
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
        return userJpaRepository.existsByEmailAndDeletedFalse(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsernameAndDeletedFalse(username);
    }
}
