package com.pwb.backend.modules.share.repository;

import com.pwb.backend.modules.share.entity.SharedThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharedThreadRepository extends JpaRepository<SharedThread, UUID> {

    Optional<SharedThread> findByProducerIdAndRecipientEmail(UUID producerId, String recipientEmail);

    @Query(value = """
            INSERT INTO shared_threads
                (id, producer_id, recipient_email, recipient_email_hash, last_interacted_at,
                 created_at, updated_at, created_by, updated_by, deleted_at, deleted_by, version)
            VALUES
                (:id, :producerId, :recipientEmail, :recipientEmailHash, NOW(),
                 NOW(), NOW(), :createdBy, :updatedBy, NULL, NULL, 0)
            ON CONFLICT (producer_id, recipient_email) DO UPDATE
                SET last_interacted_at = NOW(),
                    updated_at = NOW()
            RETURNING id
            """, nativeQuery = true)
    UUID upsertThread(@Param("id") UUID id,
                      @Param("producerId") UUID producerId,
                      @Param("recipientEmail") String recipientEmail,
                      @Param("recipientEmailHash") String recipientEmailHash,
                      @Param("createdBy") String createdBy,
                      @Param("updatedBy") String updatedBy);

    @Query(value = """
            SELECT DISTINCT recipient_email
              FROM shared_threads
             WHERE producer_id = :producerId
               AND recipient_email LIKE :escapedPrefix || '%' ESCAPE '\\'
             ORDER BY last_interacted_at DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<String> findRecipientEmailSuggestions(@Param("producerId") UUID producerId,
                                               @Param("escapedPrefix") String escapedPrefix,
                                               @Param("limit") int limit);
}