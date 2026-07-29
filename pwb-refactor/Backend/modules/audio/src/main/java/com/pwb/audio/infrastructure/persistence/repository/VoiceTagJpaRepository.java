package com.pwb.audio.infrastructure.persistence.repository;

import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VoiceTagJpaRepository extends AudioJpaRepository<VoiceTagJpaEntity> {

    @EntityGraph(attributePaths = {})
    Optional<VoiceTagJpaEntity> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {})
    Optional<VoiceTagJpaEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    boolean existsByUserIdAndNameAndDeletedFalse(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNotAndDeletedFalse(UUID userId, String name, UUID id);

    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    @Query("SELECT COUNT(vt) > 0 FROM VoiceTagJpaEntity vt " +
           "WHERE vt.id = :voiceTagId AND vt.deleted = false " +
           "AND EXISTS (SELECT 1 FROM SongTagConfigJpaEntity cfg WHERE cfg.voiceTagId = :voiceTagId AND cfg.deleted = false)")
    boolean existsByVoiceTagIdInConfig(@Param("voiceTagId") UUID voiceTagId);
}
