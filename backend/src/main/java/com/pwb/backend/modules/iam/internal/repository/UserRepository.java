package com.pwb.backend.modules.iam.internal.repository;

import com.pwb.backend.modules.iam.internal.model.User;
import com.pwb.backend.modules.iam.internal.enums.UserStatus;
import jakarta.persistence.LockModeType;
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

  Optional<User> findByEmailAndDeletedFalse(String email);

  Optional<User> findByUsernameAndDeletedFalse(String username);

  boolean existsByUsernameAndStatusAndDeletedFalse(String username, UserStatus status);

  boolean existsByEmailAndStatusAndDeletedFalse(String email, UserStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM User u WHERE (u.username = :username OR u.email = :email) "
      + "AND u.status = 'PENDING_VERIFICATION' AND u.deleted = false")
  Optional<User> findPendingUserForUpdate(
      @Param("username") String username,
      @Param("email") String email);

  @Query("SELECT u FROM User u WHERE u.status = 'PENDING_VERIFICATION' "
      + "AND u.deleted = false AND u.createdAt < :cutoff")
  List<User> findExpiredPendingUsers(@Param("cutoff") Instant cutoff);

  @Modifying
  @Query("DELETE FROM User u WHERE u.id IN :ids")
  void hardDeleteByIds(@Param("ids") List<String> ids);
}
