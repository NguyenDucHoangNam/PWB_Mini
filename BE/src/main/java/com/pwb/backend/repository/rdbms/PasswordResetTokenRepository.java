package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.PasswordResetToken;
import com.pwb.backend.repository.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends BaseRepository<PasswordResetToken> {

    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.tokenHash = :tokenHash
              AND t.used = false
              AND t.expiresAt > :now
            """)
    Optional<PasswordResetToken> findActiveByHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true, t.usedAt = :now WHERE t.userId = :userId AND t.used = false")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}