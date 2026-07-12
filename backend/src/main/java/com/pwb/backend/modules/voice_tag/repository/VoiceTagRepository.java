package com.pwb.backend.modules.voice_tag.repository;

import com.pwb.backend.modules.voice_tag.entity.VoiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoiceTagRepository extends JpaRepository<VoiceTag, UUID> {

    @Query("""
            SELECT vt FROM VoiceTag vt
            WHERE vt.id = :id AND vt.isDeleted = false
            """)
    Optional<VoiceTag> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT vt FROM VoiceTag vt
            WHERE vt.id = :id
            """)
    Optional<VoiceTag> findByIdIncludingDeleted(@Param("id") UUID id);

    @Query("""
            SELECT vt FROM VoiceTag vt
            WHERE vt.ownerId = :ownerId AND vt.isDeleted = false
            ORDER BY vt.isDefault DESC, vt.createdAt DESC
            """)
    List<VoiceTag> findAllActiveByOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            SELECT vt FROM VoiceTag vt
            WHERE vt.ownerId = :ownerId AND vt.isDefault = true AND vt.isDeleted = false
            """)
    Optional<VoiceTag> findDefaultByOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            SELECT COUNT(vt) FROM VoiceTag vt
            WHERE vt.ownerId = :ownerId AND vt.isDeleted = false
            """)
    long countActiveByOwner(@Param("ownerId") UUID ownerId);

    @Modifying
    @Query("""
            UPDATE VoiceTag vt
            SET vt.isDefault = false, vt.updatedAt = CURRENT_TIMESTAMP
            WHERE vt.ownerId = :ownerId AND vt.isDefault = true AND vt.isDeleted = false
            """)
    int clearDefaultForOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            SELECT vt FROM VoiceTag vt
            WHERE vt.ownerId = :ownerId AND vt.isDefault = true AND vt.isDeleted = false
            """)
    List<VoiceTag> findAllDefaultByOwner(@Param("ownerId") UUID ownerId);
}
