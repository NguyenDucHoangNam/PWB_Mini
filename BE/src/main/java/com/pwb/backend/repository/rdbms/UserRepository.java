package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.enums.OAuthProvider;
import com.pwb.backend.enums.UserStatus;
import com.pwb.backend.repository.BaseRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends BaseRepository<User> {

    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmailAndDeletedFalse(String email);

    @EntityGraph(attributePaths = "role")
    Optional<User> findByUsernameAndDeletedFalse(String username);

    @EntityGraph(attributePaths = "role")
    Optional<User> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = "role")
    Optional<User> findByIdAndStatusAndDeletedFalse(UUID id, UserStatus status);

    @EntityGraph(attributePaths = "role")
    Optional<User> findByOauthProviderAndOauthIdAndDeletedFalse(OAuthProvider oauthProvider, String oauthId);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndDeletedFalse(String username);
}