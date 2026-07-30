package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByIdAndStatus(UUID id, UserStatus status);

    Optional<User> findByOAuthProviderAndOAuthId(OAuthProvider provider, String oauthId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findProvisionalUsersCreatedBefore(Instant cutoff);
}
