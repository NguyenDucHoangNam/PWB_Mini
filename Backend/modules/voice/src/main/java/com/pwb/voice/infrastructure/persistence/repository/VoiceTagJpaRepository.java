package com.pwb.voice.infrastructure.persistence.repository;

import com.pwb.voice.api.enums.VoiceTagType;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface VoiceTagJpaRepository
        extends JpaRepository<VoiceTagJpaEntity, UUID>, JpaSpecificationExecutor<VoiceTagJpaEntity> {

    Optional<VoiceTagJpaEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    Page<VoiceTagJpaEntity> findByUserIdAndDeletedFalse(UUID userId, Pageable pageable);

    Page<VoiceTagJpaEntity> findByUserIdAndTagTypeAndDeletedFalse(UUID userId, VoiceTagType type, Pageable pageable);

    boolean existsByUserIdAndNameAndDeletedFalse(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNotAndDeletedFalse(UUID userId, String name, UUID id);

    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    long countByUserIdAndDeletedFalse(UUID userId);
}
