package com.pwb.voice.infrastructure.processor;

import com.pwb.voice.core.model.Song;
import com.pwb.voice.infrastructure.persistence.entity.SongJpaEntity;
import com.pwb.voice.infrastructure.persistence.mapper.SongMapper;
import com.pwb.voice.infrastructure.persistence.repository.SongJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceProcessingPersistenceService {

    private final SongJpaRepository songRepository;
    private final SongMapper songMapper;

    @Transactional
    public void markProcessed(SongJpaEntity songEntity, String processedKey, Integer durationSeconds) {
        Song song = songMapper.toDomain(songEntity);
        song.markProcessed(processedKey, durationSeconds);
        SongJpaEntity merged = songMapper.toEntity(song, songEntity);
        songRepository.save(merged);
    }

    @Transactional
    public Optional<SongJpaEntity> markFailed(UUID songId, String errorMessage) {
        return songRepository.findById(songId).map(entity -> {
            Song song = songMapper.toDomain(entity);
            song.markFailed(errorMessage == null ? "VOICE_006" : errorMessage);
            SongJpaEntity merged = songMapper.toEntity(song, entity);
            return songRepository.save(merged);
        });
    }
}