package com.pwb.voice.api;

import com.pwb.voice.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateSongRequest;
import com.pwb.voice.api.dto.request.UploadSongRequest;
import com.pwb.voice.api.dto.response.ProcessingStatusResponse;
import com.pwb.voice.api.dto.response.SongDetailResponse;
import com.pwb.voice.api.dto.response.SongResponse;
import com.pwb.voice.api.dto.response.VoiceTagConfigResponse;
import com.pwb.voice.api.enums.SongStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

public interface SongFacade {

    SongResponse uploadSong(UUID userId, MultipartFile file, UploadSongRequest request);

    Page<SongResponse> listSongs(UUID userId, SongStatus status, Pageable pageable);

    SongDetailResponse getSong(UUID userId, UUID songId);

    SongResponse updateSong(UUID userId, UUID songId, UpdateSongRequest request);

    void deleteSong(UUID userId, UUID songId);

    VoiceTagConfigResponse configureVoiceTag(UUID userId, UUID songId, ConfigureVoiceTagRequest request);

    VoiceTagConfigResponse getVoiceTagConfig(UUID userId, UUID songId);

    void removeVoiceTagConfig(UUID userId, UUID songId);

    ProcessingStatusResponse triggerProcessing(UUID userId, UUID songId);

    ProcessingStatusResponse getProcessingStatus(UUID userId, UUID songId);

    URL getStreamPresignedUrl(UUID userId, UUID songId, Duration expiration);

    URL getOriginalPresignedUrl(UUID userId, UUID songId, Duration expiration);
}
