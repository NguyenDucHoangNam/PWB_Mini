package com.pwb.voice.core.service;

import com.pwb.storage.api.StorageService;
import com.pwb.voice.api.SongFacade;
import com.pwb.voice.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateSongRequest;
import com.pwb.voice.api.dto.request.UploadSongRequest;
import com.pwb.voice.api.dto.response.ProcessingStatusResponse;
import com.pwb.voice.api.dto.response.SongDetailResponse;
import com.pwb.voice.api.dto.response.SongResponse;
import com.pwb.voice.api.dto.response.VoiceTagConfigResponse;
import com.pwb.voice.api.enums.SongStatus;
import com.pwb.voice.core.model.Song;
import com.pwb.voice.core.model.SongTagConfig;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.voice.infrastructure.persistence.repository.VoiceTagJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SongFacadeImpl implements SongFacade {

    private final SongService songService;
    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final StorageService storageService;

    @Override
    public SongResponse uploadSong(UUID userId, MultipartFile file, UploadSongRequest request) {
        Song domain = songService.uploadSong(userId, file, request);
        return toSongResponse(domain);
    }

    @Override
    public Page<SongResponse> listSongs(UUID userId, SongStatus status, Pageable pageable) {
        return songService.listSongs(userId, status, pageable).map(this::toSongResponse);
    }

    @Override
    public SongDetailResponse getSong(UUID userId, UUID songId) {
        Song domain = songService.getSong(userId, songId);
        return SongDetailResponse.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .title(domain.getTitle())
                .artist(domain.getArtist())
                .album(domain.getAlbum())
                .format(domain.getFormat())
                .status(domain.getStatus())
                .fileSizeBytes(domain.getFileSizeBytes())
                .durationSeconds(domain.getDurationSeconds())
                .processed(domain.getProcessedS3Key() != null)
                .thumbnailUrl(domain.getThumbnailUrl())
                .lastError(domain.getLastError())
                .build();
    }

    @Override
    public SongResponse updateSong(UUID userId, UUID songId, UpdateSongRequest request) {
        Song domain = songService.updateSong(userId, songId, request);
        return toSongResponse(domain);
    }

    @Override
    public void deleteSong(UUID userId, UUID songId) {
        songService.deleteSong(userId, songId);
    }

    @Override
    public VoiceTagConfigResponse configureVoiceTag(UUID userId, UUID songId, ConfigureVoiceTagRequest request) {
        SongTagConfig domain = songService.configureVoiceTag(userId, songId, request);
        return toVoiceTagConfigResponse(domain);
    }

    @Override
    public VoiceTagConfigResponse getVoiceTagConfig(UUID userId, UUID songId) {
        SongTagConfig domain = songService.getVoiceTagConfig(userId, songId);
        return toVoiceTagConfigResponse(domain);
    }

    @Override
    public void removeVoiceTagConfig(UUID userId, UUID songId) {
        songService.removeVoiceTagConfig(userId, songId);
    }

    @Override
    public ProcessingStatusResponse triggerProcessing(UUID userId, UUID songId) {
        Song domain = songService.triggerProcessing(userId, songId);
        return toProcessingStatusResponse(domain, "VOICE_PROCESSING_STARTED");
    }

    @Override
    public ProcessingStatusResponse getProcessingStatus(UUID userId, UUID songId) {
        Song domain = songService.getProcessingStatus(userId, songId);
        return toProcessingStatusResponse(domain, null);
    }

    @Override
    public URL getStreamPresignedUrl(UUID userId, UUID songId, Duration expiration) {
        String s3Key = songService.getStreamPresignedKey(userId, songId);
        return storageService.generatePresignedUrl(s3Key, expiration).getUrl();
    }

    @Override
    public URL getOriginalPresignedUrl(UUID userId, UUID songId, Duration expiration) {
        String s3Key = songService.getOriginalPresignedKey(userId, songId);
        return storageService.generatePresignedUrl(s3Key, expiration).getUrl();
    }

    private SongResponse toSongResponse(Song domain) {
        if (domain == null) {
            return null;
        }
        return SongResponse.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .title(domain.getTitle())
                .artist(domain.getArtist())
                .album(domain.getAlbum())
                .format(domain.getFormat())
                .status(domain.getStatus())
                .fileSizeBytes(domain.getFileSizeBytes())
                .durationSeconds(domain.getDurationSeconds())
                .processed(domain.getProcessedS3Key() != null)
                .build();
    }

    private VoiceTagConfigResponse toVoiceTagConfigResponse(SongTagConfig domain) {
        if (domain == null) {
            return null;
        }
        String voiceTagName = voiceTagJpaRepository
                .findById(domain.getVoiceTagId())
                .map(VoiceTagJpaEntity::getName)
                .orElse(null);

        return VoiceTagConfigResponse.builder()
                .id(domain.getId())
                .songId(domain.getSongId())
                .voiceTagId(domain.getVoiceTagId())
                .voiceTagName(voiceTagName)
                .intervalSeconds(domain.getIntervalSeconds())
                .volumePercentage(domain.getVolumePercentage())
                .fadeInDurationMs(domain.getFadeInDurationMs())
                .fadeOutDurationMs(domain.getFadeOutDurationMs())
                .startOffsetSeconds(domain.getStartOffsetSeconds())
                .enabled(domain.isEnabled())
                .build();
    }

    private ProcessingStatusResponse toProcessingStatusResponse(Song domain, String messageKey) {
        if (domain == null) {
            return null;
        }
        return ProcessingStatusResponse.builder()
                .songId(domain.getId())
                .status(domain.getStatus())
                .processedS3KeyExists(domain.getProcessedS3Key() != null)
                .lastError(domain.getLastError())
                .message(messageKey)
                .build();
    }
}
