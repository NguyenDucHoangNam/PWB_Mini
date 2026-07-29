package com.pwb.audio.infrastructure.persistence.adapter;

import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import com.pwb.audio.infrastructure.persistence.mapper.SongMapper;
import com.pwb.audio.infrastructure.persistence.repository.SongJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SongRepositoryImpl implements SongRepository {

    private final SongJpaRepository songJpaRepository;
    private final SongMapper songMapper;

    @Override
    public Song save(Song song) {
        SongJpaEntity target;
        if (song.getId() != null) {
            target = songJpaRepository.findByIdAndDeletedFalse(song.getId())
                    .orElse(null);
            target = songMapper.toEntity(song, target);
        } else {
            target = songMapper.toEntity(song, null);
        }
        SongJpaEntity saved = songJpaRepository.save(target);
        return songMapper.toDomain(saved);
    }

    @Override
    public Optional<Song> findById(UUID id) {
        return songJpaRepository.findByIdAndDeletedFalse(id)
                .map(songMapper::toDomain);
    }

    @Override
    public Optional<Song> findByIdAndUserId(UUID id, UUID userId) {
        return songJpaRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .map(songMapper::toDomain);
    }

    @Override
    public boolean existsByIdAndUserId(UUID id, UUID userId) {
        return songJpaRepository.existsByIdAndUserIdAndDeletedFalse(id, userId);
    }
}
