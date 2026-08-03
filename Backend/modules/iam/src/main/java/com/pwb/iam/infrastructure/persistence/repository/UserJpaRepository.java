package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends IamJpaRepository<UserJpaEntity> {

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByEmailAndDeletedFalse(String email);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByOauthProviderAndOauthIdAndDeletedFalse(OAuthProvider oauthProvider, String oauthId);

    boolean existsByEmailAndDeletedFalse(String email);
}
