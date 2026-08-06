package com.pwb.audio.infrastructure.persistence.adapter;

import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.audio.infrastructure.persistence.mapper.VoiceTagMapper;
import com.pwb.audio.infrastructure.persistence.repository.VoiceTagJpaRepository;
import com.pwb.audio.infrastructure.persistence.specification.VoiceTagSpecifications;
import com.pwb.audio.infrastructure.search.AudioSearchIndexWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class VoiceTagRepositoryImpl implements VoiceTagRepository {

    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final VoiceTagMapper voiceTagMapper;
    private final AudioSearchIndexWriter searchIndexWriter;

    /**
     * The search index is refreshed from the saved aggregate, not the incoming one: only the persisted
     * form carries the generated id and the audit timestamps the index sorts on.
     */
    @Override
    public VoiceTag save(VoiceTag voiceTag) {
        VoiceTag saved = voiceTag.isNew()
                ? voiceTagMapper.toDomain(voiceTagJpaRepository.save(voiceTagMapper.toEntity(voiceTag)))
                : voiceTagMapper.toDomain(voiceTagJpaRepository.save(applyToExisting(voiceTag)));
        searchIndexWriter.voiceTagSaved(saved);
        return saved;
    }

    @Override
    public Optional<VoiceTag> findById(UUID id) {
        return voiceTagJpaRepository.findById(id)
                .map(voiceTagMapper::toDomain);
    }

    @Override
    public Optional<VoiceTag> findByIdAndUserId(UUID id, UUID userId) {
        return voiceTagJpaRepository.findByIdAndUserId(id, userId)
                .map(voiceTagMapper::toDomain);
    }

    @Override
    public boolean existsByUserIdAndName(UUID userId, String name) {
        return voiceTagJpaRepository.existsByUserIdAndName(userId, name);
    }

    @Override
    public boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID id) {
        return voiceTagJpaRepository.existsByUserIdAndNameAndIdNot(userId, name, id);
    }

    @Override
    public Page<VoiceTag> findAllByUserId(UUID userId, Pageable pageable) {
        return voiceTagJpaRepository.findAllByUserId(userId, pageable)
                .map(voiceTagMapper::toDomain);
    }

    @Override
    public List<VoiceTag> findAllByIdIn(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return voiceTagJpaRepository.findAllById(ids).stream()
                .map(voiceTagMapper::toDomain)
                .toList();
    }

    @Override
    public Page<VoiceTag> search(VoiceTagSearchCriteria criteria, Pageable pageable) {
        return voiceTagJpaRepository.findAll(VoiceTagSpecifications.fromCriteria(criteria), pageable)
                .map(voiceTagMapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        voiceTagJpaRepository.deleteById(id);
        searchIndexWriter.voiceTagDeleted(id);
    }

    private VoiceTagJpaEntity applyToExisting(VoiceTag voiceTag) {
        VoiceTagJpaEntity target = loadForUpdate(voiceTag.getId());
        voiceTagMapper.applyTo(voiceTag, target);
        return target;
    }

    /**
     * An update must never silently turn into an insert: if the row is gone, the caller is working from a
     * stale aggregate and deserves to hear about it rather than get a duplicate.
     */
    private VoiceTagJpaEntity loadForUpdate(UUID id) {
        return voiceTagJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Voice tag no longer exists: " + id));
    }
}
