package com.pwb.audio.infrastructure.persistence.adapter;

import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import com.pwb.audio.infrastructure.persistence.mapper.SongTagConfigMapper;
import com.pwb.audio.infrastructure.persistence.repository.SongTagConfigJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SongTagConfigRepositoryImpl implements SongTagConfigRepository {

    private final SongTagConfigJpaRepository songTagConfigJpaRepository;
    private final SongTagConfigMapper songTagConfigMapper;

    @Override
    public SongTagConfig save(SongTagConfig config) {
        SongTagConfigJpaEntity target = songTagConfigMapper.toEntity(config);
        SongTagConfigJpaEntity saved = songTagConfigJpaRepository.save(target);
        return songTagConfigMapper.toDomain(saved);
    }

    @Override
    public Optional<SongTagConfig> findBySongId(UUID songId) {
        return songTagConfigJpaRepository.findBySongIdAndDeletedFalse(songId)
                .map(songTagConfigMapper::toDomain);
    }

    @Override
    public boolean existsBySongId(UUID songId) {
        return songTagConfigJpaRepository.existsBySongIdAndDeletedFalse(songId);
    }
}
