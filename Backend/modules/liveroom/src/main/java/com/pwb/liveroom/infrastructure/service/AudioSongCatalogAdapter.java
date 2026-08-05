package com.pwb.liveroom.infrastructure.service;

import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.service.PresignedUrl;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.liveroom.domain.service.PlayableSong;
import com.pwb.liveroom.domain.service.PlayableSongAudio;
import com.pwb.liveroom.domain.service.SongCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AudioSongCatalogAdapter implements SongCatalogPort {

    private final SongRepository songRepository;
    private final StoragePort storagePort;

    @Override
    @Transactional(readOnly = true)
    public Optional<PlayableSong> findById(UUID songId) {
        return songRepository.findById(songId).map(this::toPlayable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlayableSongAudio> presignPlayback(UUID songId, Duration expiration) {
        return songRepository.findById(songId)
                .map(Song::playbackKey)
                .filter(storageKey -> storageKey != null && !storageKey.isBlank())
                .map(storageKey -> toAudio(songId, storagePort.presignDownload(storageKey, expiration)));
    }

    private PlayableSongAudio toAudio(UUID songId, PresignedUrl presigned) {
        return new PlayableSongAudio(songId, presigned.url().toString(), presigned.expiresAt());
    }

    private PlayableSong toPlayable(Song song) {
        return new PlayableSong(
                song.getId(),
                song.getUserId(),
                song.getTitle(),
                song.getArtist(),
                song.getDurationSeconds(),


                song.isPlayable()
        );
    }
}