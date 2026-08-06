package com.pwb.audio.infrastructure.persistence.adapter;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import com.pwb.audio.infrastructure.persistence.mapper.SongMapper;
import com.pwb.audio.infrastructure.persistence.repository.SongJpaRepository;
import com.pwb.audio.infrastructure.persistence.specification.SongSpecifications;
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
public class SongRepositoryImpl implements SongRepository {

    private final SongJpaRepository songJpaRepository;
    private final SongMapper songMapper;
    private final AudioSearchIndexWriter searchIndexWriter;

    /**
     * The search index is refreshed from the saved aggregate, not the incoming one: only the persisted
     * form carries the generated id and the audit timestamps the index sorts on.
     */
    @Override
    public Song save(Song song) {
        Song saved = song.isNew()
                ? songMapper.toDomain(songJpaRepository.save(songMapper.toEntity(song)))
                : songMapper.toDomain(songJpaRepository.save(applyToExisting(song)));
        searchIndexWriter.songSaved(saved);
        return saved;
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
    public Page<Song> findAllByUserIdAndStatusIn(UUID userId, Collection<SongStatus> statuses, Pageable pageable) {
        return songJpaRepository.findAllByUserIdAndStatusIn(userId, statuses, pageable)
                .map(songMapper::toDomain);
    }

    @Override
    public List<Song> findAllByIdIn(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return songJpaRepository.findAllById(ids).stream()
                .map(songMapper::toDomain)
                .toList();
    }

    @Override
    public Page<Song> search(SongSearchCriteria criteria, Pageable pageable) {
        return songJpaRepository.findAll(SongSpecifications.fromCriteria(criteria), pageable)
                .map(songMapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        songJpaRepository.deleteById(id);
        searchIndexWriter.songDeleted(id);
    }

    private SongJpaEntity applyToExisting(Song song) {
        SongJpaEntity target = loadForUpdate(song.getId());
        songMapper.applyTo(song, target);
        return target;
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
