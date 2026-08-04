package com.pwb.audio.application.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.application.command.ConfigureVoiceTagCommand;
import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.command.VoiceTagSettings;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.enums.AudioVariant;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.model.vo.AudioFormat;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.PresignedUrl;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.StoredObject;
import com.pwb.audio.infrastructure.audio.properties.AudioUploadProperties;
import com.pwb.audio.infrastructure.processor.event.SongProcessingRequested;
import com.pwb.infra.kafka.properties.KafkaTopicProperties;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongUseCaseImpl implements SongUseCase {

    private static final String ORIGINAL_KEY_ROOT = "audio/originals/";
    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofHours(1);
    private static final Duration MIN_AUDIO_URL_EXPIRATION = Duration.ofMinutes(1);
    private static final Duration MAX_AUDIO_URL_EXPIRATION = Duration.ofDays(1);

    private final SongRepository songRepository;
    private final VoiceTagRepository voiceTagRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final StoragePort storagePort;
    private final StorageCleaner storageCleaner;
    private final AudioUploadProperties uploadProperties;
    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;

    @Override
    public UploadUrlView createUploadUrl(UUID userId, String format) {
        AudioFormat audioFormat = parseFormat(format);
        String storageKey = buildOriginalKey(userId, audioFormat);
        PresignedUrl presigned = storagePort.presignUpload(storageKey, UPLOAD_URL_EXPIRATION);

        log.debug("Issued upload URL: userId={}, storageKey={}", userId, storageKey);
        return new UploadUrlView(storageKey, presigned.url(), presigned.expiresAt());
    }

    @Override
    @Transactional
    public SongView createSong(CreateSongCommand command) {
        log.info("Creating song: userId={}, title={}", command.userId(), command.title());

        AudioFormat format = parseFormat(command.format());
        assertKeyBelongsToUser(command.userId(), command.originalS3Key());
        StoredObject uploaded = requireUploadedFile(command.originalS3Key());

        Song song = Song.create(
                command.userId(),
                command.title(),
                null,
                null,
                command.originalS3Key(),
                uploaded.sizeBytes(),
                command.durationSeconds(),
                format
        );

        // Resolve the voice tag and flip the status before the insert so creation costs a single write.
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
            log.info("Song created with inline voice tag, processing queued: songId={}, voiceTagId={}",
                    saved.getId(), voiceTag.getId());
        } else {
            log.info("Song created: songId={}", saved.getId());
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
    public Page<SongView> listSongs(UUID userId, SongStatus status, Pageable pageable) {
        Page<Song> page = status == null
                ? songRepository.findAllByUserId(userId, pageable)
                : songRepository.findAllByUserIdAndStatus(userId, status, pageable);
        return page.map(this::toSongView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SongTagConfigView> getVoiceTagConfig(UUID userId, UUID songId) {
        Song song = requireOwnedSong(userId, songId);
        return songTagConfigRepository.findBySongId(song.getId())
                .map(this::toSongTagConfigView);
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
        storageCleaner.deleteAfterCommit(song.getOriginalS3Key(), song.getProcessedS3Key());

        log.info("Song deleted: songId={}", song.getId());
    }

    /**
     * Replaces the whole configuration for a song — there is at most one per song, and every field is
     * supplied, so this is a put rather than a patch. The song's audio is left alone: the caller decides
     * when to re-run processing with the new settings.
     */
    @Override
    @Transactional
    public SongTagConfigView configureVoiceTag(ConfigureVoiceTagCommand command) {
        Song song = requireOwnedSong(command.userId(), command.songId());
        VoiceTagSettings settings = command.settings();

        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(settings.voiceTagId(), command.userId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        SongTagConfig saved = songTagConfigRepository.save(
                toTagConfig(song.getId(), voiceTag.getId(), settings));

        log.info("Voice tag configured: songId={}, voiceTagId={}", song.getId(), voiceTag.getId());
        // The tag was just loaded to authorise this call, so there is no reason to look it up again.
        return toSongTagConfigView(saved, voiceTag.getName());
    }

    @Override
    @Transactional
    public SongView triggerProcessing(UUID userId, UUID songId) {
        Song song = requireOwnedSong(userId, songId);
        String supersededKey = song.getProcessedS3Key();

        try {
            song.triggerProcessing();
        } catch (Song.ProcessingStateException ex) {
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_ALREADY_STARTED);
        }

        Song saved = songRepository.save(song);
        // Re-running replaces the previous render; without this its object would linger unreferenced.
        storageCleaner.deleteAfterCommit(supersededKey);
        publishSongProcessingRequested(saved.getId(), userId);

        log.info("Processing triggered: songId={}", saved.getId());
        return toSongView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AudioUrlView getAudioUrl(UUID userId, UUID songId, AudioVariant variant, Duration expiration) {
        assertExpirationInRange(expiration);
        Song song = requireOwnedSong(userId, songId);

        AudioVariant served = song.resolveVariant(variant);
        String storageKey = song.storageKeyFor(served);
        if (storageKey == null || storageKey.isBlank()) {
            throw new AudioBusinessException(AudioErrorCode.SONG_NOT_UPLOADED);
        }

        PresignedUrl presigned = storagePort.presignDownload(storageKey, expiration);
        return new AudioUrlView(presigned.url(), presigned.expiresAt(), served);
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
     * Closes the other half of the trust gap: owning the key says nothing about what was put there. Storage
     * is the only authority on whether a file exists and how big it really is, so both come from there
     * rather than from the request.
     */
    private StoredObject requireUploadedFile(String storageKey) {
        StoredObject uploaded = storagePort.findMetadata(storageKey)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.UPLOAD_NOT_FOUND));

        if (uploaded.sizeBytes() > uploadProperties.getMaxFileSizeBytes()) {
            log.warn("Rejected oversized upload: storageKey={}, size={}, limit={}",
                    storageKey, uploaded.sizeBytes(), uploadProperties.getMaxFileSizeBytes());
            throw new AudioBusinessException(AudioErrorCode.FILE_TOO_LARGE);
        }
        if (uploaded.sizeBytes() == 0) {
            throw new AudioBusinessException(AudioErrorCode.FILE_EMPTY);
        }
        return uploaded;
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

    private void assertExpirationInRange(Duration expiration) {
        if (expiration == null
                || expiration.compareTo(MIN_AUDIO_URL_EXPIRATION) < 0
                || expiration.compareTo(MAX_AUDIO_URL_EXPIRATION) > 0) {
            throw new IllegalArgumentException("URL expiration must be between "
                    + MIN_AUDIO_URL_EXPIRATION + " and " + MAX_AUDIO_URL_EXPIRATION);
        }
    }

    private SongTagConfig toTagConfig(UUID songId, UUID voiceTagId, VoiceTagSettings settings) {
        return SongTagConfig.create(
                songId,
                voiceTagId,
                settings.intervalSeconds(),
                settings.volumePercentage(),
                settings.duckingPercentage(),
                settings.startOffsetSeconds(),
                settings.enabled()
        );
    }

    /**
     * Carries the voice tag's name so a client rendering the configuration does not have to fetch the whole
     * tag list just to label the one it is already showing.
     */
    private SongTagConfigView toSongTagConfigView(SongTagConfig config) {
        String voiceTagName = voiceTagRepository.findById(config.getVoiceTagId())
                .map(VoiceTag::getName)
                .orElse(null);
        return toSongTagConfigView(config, voiceTagName);
    }

    private SongTagConfigView toSongTagConfigView(SongTagConfig config, String voiceTagName) {
        return new SongTagConfigView(
                config.getId(),
                config.getSongId(),
                config.getVoiceTagId(),
                voiceTagName,
                config.getIntervalSeconds(),
                config.getVolumePercentage(),
                config.getDuckingPercentage(),
                config.getStartOffsetSeconds(),
                config.isEnabled(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
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
