package com.pwb.voice.core.service;

import com.pwb.voice.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateSongRequest;
import com.pwb.voice.api.dto.request.UploadSongRequest;
import com.pwb.voice.api.enums.SongStatus;
import com.pwb.voice.core.model.Song;
import com.pwb.voice.core.model.SongTagConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface SongService {

    Song uploadSong(UUID userId, MultipartFile file, UploadSongRequest request);

    Page<Song> listSongs(UUID userId, SongStatus status, Pageable pageable);

    Song getSong(UUID userId, UUID songId);

    Song updateSong(UUID userId, UUID songId, UpdateSongRequest request);

    void deleteSong(UUID userId, UUID songId);

    SongTagConfig configureVoiceTag(UUID userId, UUID songId, ConfigureVoiceTagRequest request);

    SongTagConfig getVoiceTagConfig(UUID userId, UUID songId);

    void removeVoiceTagConfig(UUID userId, UUID songId);

    Song triggerProcessing(UUID userId, UUID songId);

    Song getProcessingStatus(UUID userId, UUID songId);

    String getStreamPresignedKey(UUID userId, UUID songId);

    String getOriginalPresignedKey(UUID userId, UUID songId);
}
