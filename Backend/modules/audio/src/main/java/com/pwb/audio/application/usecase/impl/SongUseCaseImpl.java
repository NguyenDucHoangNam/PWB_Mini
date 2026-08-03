package com.pwb.audio.application.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.application.command.ConfigureVoiceTagCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.command.UploadSongCommand;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.PresignedUploadUrlView;
import com.pwb.audio.application.view.PresignedUrlView;
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
import com.pwb.infra.kafka.properties.KafkaTopicProperties;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongUseCaseImpl implements SongUseCase {

    private static final String ORIGINAL_KEY_ROOT = "audio/originals/";
    private static final long UPLOAD_URL_EXPIRATION_SECONDS = 3600L;
    private static final long MIN_STREAM_EXPIRATION_SECONDS = 60L;
    private static final long MAX_STREAM_EXPIRATION_SECONDS = 86_400L;

    private final SongRepository songRepository;
    private final VoiceTagRepository voiceTagRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final StoragePort storagePort;
    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;

    @Override
    public PresignedUploadUrlView createUploadUrl(UUID userId, String format) {
        AudioFormat audioFormat = parseFormat(format);
        String s3Key = buildOriginalKey(userId, audioFormat);
        URL uploadUrl = storagePort.getPresignedUploadUrl(s3Key, UPLOAD_URL_EXPIRATION_SECONDS);

        log.debug("Issued upload URL: userId={}, s3Key={}", userId, s3Key);
        return new PresignedUploadUrlView(s3Key, uploadUrl, UPLOAD_URL_EXPIRATION_SECONDS);
    }

    @Override
    @Transactional
    public SongView uploadSong(UploadSongCommand command) {
        log.info("Uploading song: userId={}, title={}", command.userId(), command.title());

        AudioFormat format = parseFormat(command.format());
        assertKeyBelongsToUser(command.userId(), command.originalS3Key());

        Song song = Song.create(
                command.userId(),
                command.title(),
                null,
                null,
                command.originalS3Key(),
                command.fileSizeBytes(),
                command.durationSeconds(),
                format
        );

        // Resolve the voice tag and flip the status before the insert so the whole upload costs one write.
        VoiceTag voiceTag = null;
        if (command.voiceTagConfig() != null) {
            voiceTag = voiceTagRepository.findByIdAndUserId(command.voiceTagConfig().voiceTagId(), command.userId())
                    .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));
            song.triggerProcessing();
        }

        Song saved = songRepository.save(song);

        if (voiceTag != null) {
            songTagConfigRepository.save(toTagConfig(saved.getId(), voiceTag.getId(), command.voiceTagConfig()));
            publishSongProcessingRequested(saved.getId(), command.userId());
            log.info("Song uploaded with inline voice tag, processing queued: songId={}, voiceTagId={}",
                    saved.getId(), voiceTag.getId());
        } else {
            log.info("Song uploaded: songId={}", saved.getId());
        }

        return toSongView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SongView getSong(UUID userId, UUID songId) {
        return toSongView(requireOwnedSong(userId, songId));
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
        Song song = requireOwnedSong(command.userId(), command.songId());

        song.updateMetadata(command.title());
        Song saved = songRepository.save(song);

        log.info("Song updated: songId={}", saved.getId());
        return toSongView(saved);
    }

    @Override
    @Transactional
    public void deleteSong(DeleteSongCommand command) {
        Song song = requireOwnedSong(command.userId(), command.songId());

        songTagConfigRepository.deleteBySongId(song.getId());
        songRepository.deleteById(song.getId());
        deleteStoredObjectsAfterCommit(song);

        log.info("Song deleted: songId={}", song.getId());
    }

    @Override
    @Transactional
    public SongView triggerProcessing(UUID userId, UUID songId) {
        Song song = requireOwnedSong(userId, songId);

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

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlView getStreamPresignedUrl(UUID userId, UUID songId, long expirationSeconds) {
        assertExpirationInRange(expirationSeconds);
        Song song = requireOwnedSong(userId, songId);

        String s3Key = song.isProcessed() ? song.getProcessedS3Key() : song.getOriginalS3Key();
        if (s3Key == null || s3Key.isBlank()) {
            throw new AudioBusinessException(AudioErrorCode.SONG_NOT_UPLOADED);
        }

        URL presignedUrl = storagePort.getPresignedUrl(s3Key, expirationSeconds);
        return new PresignedUrlView(songId, presignedUrl, expirationSeconds);
    }

    private Song requireOwnedSong(UUID userId, UUID songId) {
        return songRepository.findByIdAndUserId(songId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));
    }

    private AudioFormat parseFormat(String rawFormat) {
        try {
            return AudioFormat.of(rawFormat);
        } catch (IllegalArgumentException ex) {
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FORMAT);
        }
    }

    private String buildOriginalKey(UUID userId, AudioFormat format) {
        return ORIGINAL_KEY_ROOT + userId + "/" + UUID.randomUUID() + "." + format.value();
    }

    /**
     * The client hands back the key it uploaded to, so it could just as easily hand back somebody else's.
     * Only keys under the caller's own prefix — with no traversal segments — are accepted.
     */
    private void assertKeyBelongsToUser(UUID userId, String s3Key) {
        String expectedPrefix = ORIGINAL_KEY_ROOT + userId + "/";
        if (s3Key == null || !s3Key.startsWith(expectedPrefix) || s3Key.contains("..")) {
            log.warn("Rejected upload with foreign or malformed storage key: userId={}, s3Key={}", userId, s3Key);
            throw new AudioBusinessException(AudioErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private void assertExpirationInRange(long expirationSeconds) {
        if (expirationSeconds < MIN_STREAM_EXPIRATION_SECONDS || expirationSeconds > MAX_STREAM_EXPIRATION_SECONDS) {
            throw new IllegalArgumentException("expirationSeconds must be between "
                    + MIN_STREAM_EXPIRATION_SECONDS + " and " + MAX_STREAM_EXPIRATION_SECONDS);
        }
    }

    private SongTagConfig toTagConfig(UUID songId, UUID voiceTagId, ConfigureVoiceTagCommand vtConfig) {
        return SongTagConfig.create(
                songId,
                voiceTagId,
                vtConfig.intervalSeconds(),
                vtConfig.volumePercentage(),
                vtConfig.fadeInDurationMs(),
                vtConfig.fadeOutDurationMs(),
                vtConfig.startOffsetSeconds(),
                vtConfig.enabled()
        );
    }

    /**
     * Object storage takes no part in the transaction: dropping the files before the commit would leave a
     * surviving song pointing at nothing if the transaction later rolled back.
     */
    private void deleteStoredObjectsAfterCommit(Song song) {
        List<String> keys = Stream.of(song.getOriginalS3Key(), song.getProcessedS3Key())
                .filter(key -> key != null && !key.isBlank())
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(this::deleteQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(SongUseCaseImpl.this::deleteQuietly);
            }
        });
    }

    /**
     * The row is already gone by this point, so a storage failure must not surface as a failed delete —
     * it is logged loudly enough for an operator to reclaim the orphan.
     */
    private void deleteQuietly(String s3Key) {
        try {
            storagePort.delete(s3Key);
            log.debug("Deleted audio object: s3Key={}", s3Key);
        } catch (Exception ex) {
            log.error("Orphaned audio object, manual cleanup required: s3Key={}", s3Key, ex);
        }
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
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        }
    }

    private SongView toSongView(Song song) {
        return new SongView(
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
                song.getLastError(),
                song.isProcessed(),
                song.getCreatedAt(),
                song.getUpdatedAt()
        );
    }
}
