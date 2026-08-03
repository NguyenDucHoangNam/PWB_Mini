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
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    /**
     * Loads the managed entity first so an update mutates the existing row rather than detaching
     * and re-attaching it. Inside an active persistence context that lookup is served from the
     * first-level cache — the entity was almost always already read by the use case — so it costs
     * a query only when saving an entity this transaction has not touched.
     */
    @Override
    public User save(User user) {
        UserJpaEntity target = userJpaRepository.findByIdAndDeletedFalse(user.getUserId())
                .map(existing -> userMapper.toEntity(user, existing))
                .orElseGet(() -> userMapper.toEntity(user, null));
        return userMapper.toDomain(userJpaRepository.save(target));
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
    public Optional<User> findByOAuthProviderAndOAuthId(OAuthProvider provider, String oauthId) {
        return userJpaRepository.findByOauthProviderAndOauthIdAndDeletedFalse(provider, oauthId)
                .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && userJpaRepository.existsByEmailAndDeletedFalse(email.toLowerCase());
    }
}
