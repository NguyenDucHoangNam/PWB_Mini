package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends IamJpaRepository<UserJpaEntity> {

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByEmailAndDeletedFalse(String email);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByUsernameAndDeletedFalse(String username);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByIdAndStatusAndDeletedFalse(UUID id, UserStatus status);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByOauthProviderAndOauthIdAndDeletedFalse(OAuthProvider oauthProvider, String oauthId);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndDeletedFalse(String username);

    @EntityGraph(attributePaths = "role")
    @Query("SELECT u FROM UserJpaEntity u WHERE u.provisionalUsername = true AND u.createdAt < :cutoff AND u.status = com.pwb.iam.domain.model.UserStatus.ACTIVE AND u.deleted = false")
    List<UserJpaEntity> findProvisionalUsersCreatedBefore(@Param("cutoff") Instant cutoff);
}
