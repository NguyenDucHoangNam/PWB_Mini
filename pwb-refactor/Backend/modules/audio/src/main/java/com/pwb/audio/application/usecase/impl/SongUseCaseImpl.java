package com.pwb.audio.application.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.application.command.*;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.model.vo.AudioFormat;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.infrastructure.processor.event.SongProcessingRequested;
import com.pwb.audio.infrastructure.service.StoragePort;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import com.pwb.infra.kafka.properties.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongUseCaseImpl implements SongUseCase {

    private final SongRepository songRepository;
    private final VoiceTagRepository voiceTagRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final StoragePort storagePort;
    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SongView uploadSong(UploadSongCommand command) {
        log.info("Uploading song: userId={}, title={}", command.userId(), command.title());

        AudioFormat format = AudioFormat.of(command.format());

        Song song = Song.create(
                command.userId(),
                command.title(),
                command.artist(),
                command.album(),
                command.originalS3Key(),
                command.fileSizeBytes(),
                command.durationSeconds(),
                format
        );

        Song saved = songRepository.save(song);
        log.info("Song uploaded: songId={}", saved.getId());

        return toSongView(saved);
    }

    @Override
    @Transactional
    public SongView uploadSongMultipart(UploadSongMultipartCommand command) {
        log.info("Uploading song (multipart): userId={}, title={}, format={}",
                command.userId(), command.title(), command.format().value());

        Song song = Song.create(
                command.userId(),
                command.title(),
                command.artist(),
                command.album(),
                command.originalS3Key(),
                command.fileSizeBytes(),
                command.durationSeconds(),
                command.format()
        );

        Song saved = songRepository.save(song);
        log.info("Song uploaded (multipart): songId={}", saved.getId());

        return toSongView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SongView getSong(UUID userId, UUID songId) {
        Song song = songRepository.findByIdAndUserId(songId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        return toSongView(song);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SongView> listSongs(UUID userId, Pageable pageable) {
        return songRepository.findAllByUserId(userId, pageable)
                .map(this::toSongView);
    }

    @Override
    @Transactional
    public SongView updateSong(UpdateSongCommand command) {
        Song song = songRepository.findByIdAndUserId(command.songId(), command.userId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        song.updateMetadata(command.title(), command.artist(), command.album());
        Song saved = songRepository.save(song);

        log.info("Song updated: songId={}", saved.getId());
        return toSongView(saved);
    }

    @Override
    @Transactional
    public void deleteSong(DeleteSongCommand command) {
        Song song = songRepository.findByIdAndUserId(command.songId(), command.userId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        song.markDeleted();
        songRepository.save(song);

        log.info("Song deleted: songId={}", song.getId());
    }

    @Override
    @Transactional
    public SongTagConfigView configureVoiceTag(UUID userId, ConfigureVoiceTagCommand command) {
        Song song = songRepository.findByIdAndUserId(command.songId(), userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(command.voiceTagId(), userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        SongTagConfig config = SongTagConfig.create(
                song.getId(),
                voiceTag.getId(),
                command.intervalSeconds(),
                command.volumePercentage(),
                command.fadeInDurationMs(),
                command.fadeOutDurationMs(),
                command.startOffsetSeconds(),
                command.enabled()
        );

        SongTagConfig saved = songTagConfigRepository.save(config);
        log.info("Voice tag configured: songId={}, configId={}", song.getId(), saved.getId());

        return toSongTagConfigView(saved);
    }

    @Override
    @Transactional
    public SongView triggerProcessing(UUID userId, UUID songId) {
        Song song = songRepository.findByIdAndUserId(songId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        try {
            song.triggerProcessing();
        } catch (Song.ProcessingStateException ex) {
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_ALREADY_STARTED);
        }

        Song saved = songRepository.save(song);
        publishSongProcessingRequested(saved.getId(), userId);

        log.info("Processing triggered: songId={}", saved.getId());
        return toSongView(saved);
    }

    private void publishSongProcessingRequested(UUID songId, UUID userId) {
        SongProcessingRequested event = new SongProcessingRequested(songId, userId, Instant.now());
        try {
            String json = objectMapper.writeValueAsString(event);
            outboxEnqueueHelper.enqueue(
                    KafkaTopicProperties.TOPIC_VOICE_PROCESSING,
                    "Song",
                    songId.toString(),
                    json
            );
        } catch (JsonProcessingException ex) {
            log.warn("Song processing event serialization failed: songId={}", songId, ex);
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds) {
        Song song = songRepository.findByIdAndUserId(songId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        String s3Key = song.isProcessed() ? song.getProcessedS3Key() : song.getOriginalS3Key();
        if (s3Key == null || s3Key.isBlank()) {
            throw new AudioBusinessException(AudioErrorCode.SONG_NOT_UPLOADED);
        }

        URL presignedUrl = storagePort.getPresignedUrl(s3Key, expirationSeconds);

        return new PresignedUrlView(songId, presignedUrl, expirationSeconds);
    }

    private SongView toSongView(Song song) {
        return SongView.from(
                song.getId(),
                song.getUserId(),
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getOriginalS3Key(),
                song.getProcessedS3Key(),
                song.getFileSizeBytes(),
                song.getDurationSeconds(),
                song.getFormat() != null ? song.getFormat().value() : null,
                song.getStatus(),
                song.getThumbnailUrl(),
                song.getCreatedAt(),
                song.getUpdatedAt()
        );
    }

    private SongTagConfigView toSongTagConfigView(SongTagConfig config) {
        return new SongTagConfigView(
                config.getId(),
                config.getSongId(),
                config.getVoiceTagId(),
                config.getIntervalSeconds(),
                config.getVolumePercentage(),
                config.getFadeInDurationMs(),
                config.getFadeOutDurationMs(),
                config.getStartOffsetSeconds(),
                config.isEnabled(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
