package com.pwb.backend.modules.iam.repository;

import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("""
        select u from User u
        where u.email = :email
          and u.deletedAt is null
        """)
    Optional<User> findByEmail(@Param("email") String email);

    @Query("""
        select case when count(u) > 0 then true else false end from User u
        where u.email = :email
          and u.deletedAt is null
        """)
    boolean existsByEmail(@Param("email") String email);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u from User u
        where u.email = :email
          and u.deletedAt is null
        """)
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u from User u
        where u.id = :id
          and u.deletedAt is null
        """)
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        select u from User u
        where u.oauthProvider = :provider
          and u.oauthId = :oauthId
          and u.deletedAt is null
        """)
    Optional<User> findByOauthProviderAndOauthId(@Param("provider") OauthProvider provider,
                                                @Param("oauthId") String oauthId);

    @Query("""
        select u from User u
        where u.status = UserStatus.PENDING_DELETION
          and u.deletionRequestedAt is not null
          and u.deletionRequestedAt <= :threshold
        order by u.deletionRequestedAt asc
        """)
    List<User> findExpiredPendingDeletion(@Param("threshold") Instant threshold,
                                          Pageable pageable);

    @Query("""
        select count(u) from User u
        where u.status = UserStatus.PENDING_DELETION
          and u.deletionRequestedAt is not null
          and u.deletionRequestedAt <= :threshold
        """)
    long countExpiredPendingDeletion(@Param("threshold") Instant threshold);
}
