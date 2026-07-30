package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndStatus(UUID id, UserStatus status);

    Optional<User> findByOAuthProviderAndOAuthId(OAuthProvider provider, String oauthId);

    boolean existsByEmail(String email);
}
