package com.pwb.liveroom.infrastructure.service;

import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.liveroom.domain.service.PlayableSong;
import com.pwb.liveroom.domain.service.SongCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AudioSongCatalogAdapter implements SongCatalogPort {

    private final SongRepository songRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PlayableSong> findById(UUID songId) {
        return songRepository.findById(songId).map(this::toPlayable);
    }

    private PlayableSong toPlayable(Song song) {
        return new PlayableSong(
                song.getId(),
                song.getUserId(),
                song.getTitle(),
                song.getArtist(),
                song.getDurationSeconds(),
                song.isProcessed()
        );
    }
}