package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByOAuthProviderAndOAuthId(OAuthProvider provider, String oauthId);

    boolean existsByEmail(String email);

    Page<User> findAll(UserSearchCriteria criteria, Pageable pageable);

    long countByStatus(UserStatus status);

    long countByRole(RoleName role);

    long countAll();

    long countCreatedAfter(Instant after);
}
