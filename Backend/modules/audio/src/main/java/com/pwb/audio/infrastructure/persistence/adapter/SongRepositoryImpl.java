package com.pwb.audio.infrastructure.persistence.adapter;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import com.pwb.audio.infrastructure.persistence.mapper.SongMapper;
import com.pwb.audio.infrastructure.persistence.repository.SongJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        if (song.isNew()) {
            return songMapper.toDomain(songJpaRepository.save(songMapper.toEntity(song)));
        }
        SongJpaEntity target = loadForUpdate(song.getId());
        songMapper.applyTo(song, target);
        return songMapper.toDomain(songJpaRepository.save(target));
    }

    @Override
    public Optional<Song> findById(UUID id) {
        return songJpaRepository.findById(id)
                .map(songMapper::toDomain);
    }

    @Override
    public Optional<Song> findByIdAndUserId(UUID id, UUID userId) {
        return songJpaRepository.findByIdAndUserId(id, userId)
                .map(songMapper::toDomain);
    }

    @Override
    public Page<Song> findAllByUserId(UUID userId, Pageable pageable) {
        return songJpaRepository.findAllByUserId(userId, pageable)
                .map(songMapper::toDomain);
    }

    @Override
    public Page<Song> findAllByUserIdAndStatus(UUID userId, SongStatus status, Pageable pageable) {
        return songJpaRepository.findAllByUserIdAndStatus(userId, status, pageable)
                .map(songMapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        songJpaRepository.deleteById(id);
    }

    /**
     * An update must never silently turn into an insert: if the row is gone, the caller is working from a
     * stale aggregate and deserves to hear about it rather than get a duplicate.
     */
    private SongJpaEntity loadForUpdate(UUID id) {
        return songJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Song no longer exists: " + id));
    }
}
