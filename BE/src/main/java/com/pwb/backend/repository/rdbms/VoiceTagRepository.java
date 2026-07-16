package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.VoiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoiceTagRepository extends JpaRepository<VoiceTag, UUID> {

    List<VoiceTag> findAllByOwnerIdOrderByIsDefaultDescCreatedAtDesc(UUID ownerId);

    Optional<VoiceTag> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    @Modifying
    @Query("UPDATE VoiceTag v SET v.isDefault = false, v.updatedBy = :actor " +
            "WHERE v.ownerId = :ownerId AND v.id <> :excludeId AND v.isDefault = true")
    int clearDefaultForOwner(@Param("ownerId") UUID ownerId,
                             @Param("excludeId") UUID excludeId,
                             @Param("actor") String actor);
}
