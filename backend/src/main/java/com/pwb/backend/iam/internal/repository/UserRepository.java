package com.pwb.backend.iam.internal.repository;

import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

  @EntityGraph(attributePaths = "role")
  Optional<User> findByEmailAndDeletedFalse(String email);

  @EntityGraph(attributePaths = "role")
  @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username) AND u.deleted = false")
  Optional<User> findByUsernameAndDeletedFalse(@Param("username") String username);

  @EntityGraph(attributePaths = "role")
  @Query("SELECT u FROM User u WHERE (LOWER(u.username) = LOWER(:usernameOrEmail) "
      + "OR LOWER(u.email) = LOWER(:usernameOrEmail)) AND u.deleted = false")
  Optional<User> findByUsernameOrEmailAndDeletedFalse(@Param("usernameOrEmail") String usernameOrEmail);

  @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.username) = LOWER(:username) "
      + "AND u.status = :status AND u.deleted = false")
  boolean existsByUsernameAndStatusAndDeletedFalse(@Param("username") String username,
                                                    @Param("status") UserStatus status);

  @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email) "
      + "AND u.status = :status AND u.deleted = false")
  boolean existsByEmailAndStatusAndDeletedFalse(@Param("email") String email,
                                                @Param("status") UserStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM User u WHERE (LOWER(u.username) = LOWER(:username) "
      + "OR LOWER(u.email) = LOWER(:email)) "
      + "AND u.status = 'PENDING_VERIFICATION' AND u.deleted = false")
  Optional<User> findPendingUserForUpdate(
      @Param("username") String username,
      @Param("email") String email);

  @Query("SELECT u FROM User u WHERE u.status = 'PENDING_VERIFICATION' "
      + "AND u.deleted = false AND u.createdAt < :cutoff")
  List<User> findExpiredPendingUsers(@Param("cutoff") Instant cutoff);

  @Query("SELECT u FROM User u WHERE u.status = 'PENDING_DELETION' "
      + "AND u.deleted = false AND u.deletionRequestedAt <= :cutoff")
  List<User> findUsersPendingDeletionBefore(
      @Param("cutoff") Instant cutoff,
      org.springframework.data.domain.Pageable pageable);

  @Modifying
  @Query("UPDATE User u SET u.deleted = true, u.deletedAt = CURRENT_TIMESTAMP, "
      + "u.status = 'ANONYMIZED' WHERE u.id IN :ids AND u.deleted = false")
  int softDeleteByIds(@Param("ids") List<String> ids);
}
