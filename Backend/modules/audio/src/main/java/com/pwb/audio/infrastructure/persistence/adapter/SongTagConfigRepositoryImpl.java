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

    /**
     * {@code song_id} is unique, so an existing config is updated in place rather than inserted twice.
     */
    @Override
    public SongTagConfig save(SongTagConfig config) {
        SongTagConfigJpaEntity target = songTagConfigJpaRepository.findBySongId(config.getSongId())
                .map(existing -> songTagConfigMapper.toEntity(config, existing))
                .orElseGet(() -> songTagConfigMapper.toEntity(config));
        return songTagConfigMapper.toDomain(songTagConfigJpaRepository.save(target));
    }

    @Override
    public Optional<SongTagConfig> findBySongId(UUID songId) {
        return songTagConfigJpaRepository.findBySongId(songId)
                .map(songTagConfigMapper::toDomain);
    }

    @Override
    public void deleteBySongId(UUID songId) {
        songTagConfigJpaRepository.deleteBySongId(songId);
    }
}
