package com.pwb.audio.infrastructure.persistence.adapter;

import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.audio.infrastructure.persistence.mapper.VoiceTagMapper;
import com.pwb.audio.infrastructure.persistence.repository.VoiceTagJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class VoiceTagRepositoryImpl implements VoiceTagRepository {

    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final VoiceTagMapper voiceTagMapper;

    @Override
    public VoiceTag save(VoiceTag voiceTag) {
        VoiceTagJpaEntity target;
        if (voiceTag.getId() != null) {
            target = voiceTagJpaRepository.findByIdAndDeletedFalse(voiceTag.getId())
                    .orElse(null);
            target = voiceTagMapper.toEntity(voiceTag, target);
        } else {
            target = voiceTagMapper.toEntity(voiceTag, null);
        }
        VoiceTagJpaEntity saved = voiceTagJpaRepository.save(target);
        return voiceTagMapper.toDomain(saved);
    }

    @Override
    public Optional<VoiceTag> findById(UUID id) {
        return voiceTagJpaRepository.findByIdAndDeletedFalse(id)
                .map(voiceTagMapper::toDomain);
    }

    @Override
    public Optional<VoiceTag> findByIdAndUserId(UUID id, UUID userId) {
        return voiceTagJpaRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .map(voiceTagMapper::toDomain);
    }

    @Override
    public boolean existsByUserIdAndName(UUID userId, String name) {
        return voiceTagJpaRepository.existsByUserIdAndNameAndDeletedFalse(userId, name);
    }

    @Override
    public boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID id) {
        return voiceTagJpaRepository.existsByUserIdAndNameAndIdNotAndDeletedFalse(userId, name, id);
    }

    @Override
    public boolean existsByIdAndUserId(UUID id, UUID userId) {
        return voiceTagJpaRepository.existsByIdAndUserIdAndDeletedFalse(id, userId);
    }

    @Override
    public boolean existsByVoiceTagIdInConfig(UUID voiceTagId) {
        return voiceTagJpaRepository.existsByVoiceTagIdInConfig(voiceTagId);
    }
}
