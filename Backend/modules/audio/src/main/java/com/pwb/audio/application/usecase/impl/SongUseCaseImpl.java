package com.pwb.audio.application.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.application.command.CreateSongCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.command.VoiceTagSettings;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
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
import com.pwb.infra.storage.util.MediaTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongUseCaseImpl implements SongUseCase {

    /**
     * Where an upload lands, and where it stays only until it is registered.
     *
     * <p>Nothing under this prefix is ever referenced by a song: {@link #createSong} promotes the object
     * to {@link #ORIGINAL_KEY_ROOT} before writing the row. That is what makes an expiry lifecycle rule on
     * this prefix safe — anything still here after a day is, by construction, an upload nobody ever
     * registered, and no other mechanism can find those. See {@code docs/storage/bucket-configuration.md}.
     *
     * <p>The separation is the whole point. While uploads landed straight in {@code audio/originals/}, that
     * one prefix held abandoned uploads and live audio side by side, so no rule could remove the first
     * without risking the second.
     */
    private static final String STAGING_KEY_ROOT = "audio/staging/";

    /** Where a registered song's audio lives. Never written to directly — only promoted into. */
    private static final String ORIGINAL_KEY_ROOT = "audio/originals/";

    /**
     * Long enough for a slow connection to finish a 200 MB upload, short enough that an abandoned URL
     * stops being usable well before the bucket lifecycle rule sweeps what it left behind.
     */
    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofHours(1);

    /**
     * Fixed rather than chosen by the caller. A presigned URL is a bearer credential that nothing can
     * revoke, so its lifetime is a security setting and not a client preference — the previous
     * caller-supplied value allowed 24 hours, and the client never asked for anything but the default.
     * The player renews well before this elapses, so a short window costs nothing.
     */
    private static final Duration AUDIO_URL_EXPIRATION = Duration.ofMinutes(15);

    private final SongRepository songRepository;
    private final VoiceTagRepository voiceTagRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final StoragePort storagePort;
    private final StorageCleaner storageCleaner;
    private final AudioUploadProperties uploadProperties;
    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;
    private final SongViewFactory songViewFactory;

    /**
     * The size limit is applied here, before the URL exists, because this is the only moment it can be
     * applied at all: the limit becomes part of the signature, so storage refuses a larger body outright
     * rather than accepting it and letting the later registration reject it. Checked afterwards, an
     * oversized file is already transferred, already billed, and already sitting in the bucket with no
     * row to point at it.
     *
     * <p>The content type is decided here too, from the declared format, and returned to the client so it
     * can send back the exact value that was signed. Leaving it unsigned let the client label an upload
     * anything it liked — {@code text/html} included, which storage then served as a page.
     */
    @Override
    public UploadUrlView createUploadUrl(UUID userId, String format, long sizeBytes) {
        AudioFormat audioFormat = parseFormat(format);
        assertUploadSizeAllowed(sizeBytes);

        String contentType = contentTypeFor(audioFormat);
        String storageKey = buildStagingKey(userId, audioFormat);
        PresignedUrl presigned =
                storagePort.presignUpload(storageKey, contentType, sizeBytes, UPLOAD_URL_EXPIRATION);

        log.debug("Issued upload URL: userId={}, storageKey={}, contentType={}, size={}",
                userId, storageKey, contentType, sizeBytes);
        return new UploadUrlView(storageKey, presigned.url(), contentType, presigned.expiresAt());
    }

    /**
     * Registering an upload also <em>promotes</em> it: the object is copied out of the staging prefix into
     * {@link #ORIGINAL_KEY_ROOT}, and the staged copy is dropped once the row is safely committed. That
     * move is what makes the staging area disposable — see {@link #STAGING_KEY_ROOT}.
     *
     * <p>The ordering matters in both directions. The copy happens before the write, so a row never points
     * at an object that does not exist yet; the staged original is removed only after commit, so a
     * rollback leaves the upload where the client left it rather than destroying it.
     */
    @Override
    @Transactional
    public SongView createSong(CreateSongCommand command) {
        log.info("Creating song: userId={}, title={}", command.userId(), command.title());

        AudioFormat format = parseFormat(command.format());
        String stagingKey = command.originalS3Key();
        assertKeyBelongsToUser(command.userId(), stagingKey);

        String songKey = toSongKey(stagingKey);
        assertKeyNotAlreadyRegistered(songKey);
        StoredObject uploaded = requireUploadedFile(stagingKey);

        VoiceTag voiceTag = null;
        if (command.voiceTagConfig() != null) {
            voiceTag = voiceTagRepository.findByIdAndUserId(command.voiceTagConfig().voiceTagId(), command.userId())
                    .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));
        }

        storagePort.copy(stagingKey, songKey);

        Song saved;
        try {
            saved = persist(command, format, songKey, uploaded.sizeBytes(), voiceTag);
        } catch (DataIntegrityViolationException ex) {
            // The check above is not atomic; the unique index on original_s3_key is what actually decides.
            // The copy is deliberately left alone: losing this race means another registration owns that
            // key now, and the object sitting there is its live audio rather than ours to clean up.
            log.warn("Rejected duplicate registration of a storage key: userId={}, s3Key={}",
                    command.userId(), songKey);
            throw new AudioBusinessException(AudioErrorCode.UPLOAD_ALREADY_REGISTERED, ex);
        } catch (RuntimeException ex) {
            // Nothing references the copy, and it sits outside the staging prefix the lifecycle rule
            // sweeps, so this is the only chance to reclaim it.
            storageCleaner.deleteNow(songKey);
            throw ex;
        }

        storageCleaner.deleteAfterCommit(stagingKey);

        return songViewFactory.toView(saved, voiceTag != null);
    }

    /** The database half of {@link #createSong}, kept together so one failure undoes all of it. */
    private Song persist(
            CreateSongCommand command, AudioFormat format, String songKey, long sizeBytes, VoiceTag voiceTag) {
        Song song = Song.create(
                command.userId(),
                command.title(),
                null,
                null,
                songKey,
                sizeBytes,
                command.durationSeconds(),
                format
        );

        // Flip the status before the insert so creation costs a single write.
        if (voiceTag != null) {
            song.startProcessing();
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
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public SongView getSong(UUID userId, UUID songId) {
        Song song = requireOwnedSong(userId, songId);
        return songViewFactory.toView(song);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SongView> listSongs(UUID userId, Collection<SongStatus> statuses, Pageable pageable) {
        Page<Song> page = (statuses == null || statuses.isEmpty())
                ? songRepository.findAllByUserId(userId, pageable)
                : songRepository.findAllByUserIdAndStatusIn(userId, statuses, pageable);

        Set<UUID> taggedSongIds = songViewFactory.resolveTaggedSongIds(page.getContent());
        return page.map(song -> songViewFactory.toView(song, taggedSongIds.contains(song.getId())));
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
        return songViewFactory.toView(saved);
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
     * A failed merge produced no audio, so there is nothing to supersede and nothing to clean up — this
     * re-runs the first render rather than replacing a finished one.
     */
    @Override
    @Transactional
    public SongView retryProcessing(UUID userId, UUID songId) {
        Song song = requireOwnedSong(userId, songId);

        try {
            song.retryProcessing();
        } catch (Song.ProcessingStateException ex) {
            throw new AudioBusinessException(AudioErrorCode.RETRY_NOT_ALLOWED);
        }

        Song saved = songRepository.save(song);
        publishSongProcessingRequested(saved.getId(), userId);

        log.info("Processing retried: songId={}", saved.getId());
        return songViewFactory.toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AudioUrlView getAudioUrl(UUID userId, UUID songId) {
        Song song = requireOwnedSong(userId, songId);

        String storageKey = song.playbackKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new AudioBusinessException(AudioErrorCode.SONG_NOT_UPLOADED);
        }

        PresignedUrl presigned = storagePort.presignDownload(storageKey, AUDIO_URL_EXPIRATION);
        return new AudioUrlView(presigned.url(), presigned.expiresAt());
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

    private String buildStagingKey(UUID userId, AudioFormat format) {
        return STAGING_KEY_ROOT + userId + "/" + UUID.randomUUID() + "." + format.value();
    }

    /**
     * The registered home of a staged upload: same owner, same name, different prefix.
     *
     * <p>Derived rather than freshly generated so the mapping stays one-to-one — two staged uploads can
     * never land on the same song key, which is what lets the unique index on that column mean what it
     * says. Safe as a plain prefix swap because {@link #assertKeyBelongsToUser} has already established
     * that the key starts with the staging root and contains no traversal.
     */
    private String toSongKey(String stagingKey) {
        return ORIGINAL_KEY_ROOT + stagingKey.substring(STAGING_KEY_ROOT.length());
    }

    /**
     * Closes the other half of the trust gap: owning the key says nothing about what was put there. Storage
     * is the only authority on whether a file exists and how big it really is, so both come from there
     * rather than from the request.
     *
     * <p>The size check is a backstop now that the presigned URL pins the length — it still catches an
     * object uploaded under an older URL, or one written by anything other than this flow.
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
        assertReallyAudio(storageKey);
        return uploaded;
    }

    /**
     * Reads the first bytes back out of storage and checks them against the audio container signatures.
     *
     * <p>Nothing before this point has seen the file's contents: the key carries an extension the client
     * chose, and the content type is a header the client sent. Both describe what the uploader says the
     * file is. A song without a voice tag is never decoded by anything server-side either, so without
     * this check arbitrary bytes could be stored, registered as a song, and handed back to listeners.
     *
     * <p>The read is ranged, so this costs a few bytes rather than the whole upload. The voice tag path
     * reaches the same conclusion by running ffprobe over bytes it already holds in memory.
     */
    private void assertReallyAudio(String storageKey) {
        byte[] head = storagePort.readHead(storageKey, MediaTypeUtils.MAGIC_BYTE_COUNT);
        if (!MediaTypeUtils.isAudio(head)) {
            log.warn("Rejected upload whose contents are not audio: storageKey={}", storageKey);
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE);
        }
    }

    private void assertUploadSizeAllowed(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new AudioBusinessException(AudioErrorCode.FILE_EMPTY);
        }
        if (sizeBytes > uploadProperties.getMaxFileSizeBytes()) {
            log.warn("Refused to issue an upload URL for an oversized file: size={}, limit={}",
                    sizeBytes, uploadProperties.getMaxFileSizeBytes());
            throw new AudioBusinessException(AudioErrorCode.FILE_TOO_LARGE);
        }
    }

    /**
     * One stored object backs exactly one song. Without this the same key could be registered twice, and
     * deleting either song would delete the object out from under the other — leaving a row that lists
     * normally but whose audio has gone.
     */
    private void assertKeyNotAlreadyRegistered(String s3Key) {
        if (songRepository.existsByOriginalS3Key(s3Key)) {
            throw new AudioBusinessException(AudioErrorCode.UPLOAD_ALREADY_REGISTERED);
        }
    }

    private String contentTypeFor(AudioFormat format) {
        return switch (format.value()) {
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            default -> "audio/mpeg";
        };
    }

    /**
     * The client hands back the key it uploaded to, so it could just as easily hand back somebody else's.
     * Only keys under the caller's own staging prefix — with no traversal segments — are accepted.
     *
     * <p>Restricting this to the staging root also stops a caller naming an already-registered song's key
     * and having it promoted a second time.
     */
    private void assertKeyBelongsToUser(UUID userId, String s3Key) {
        String expectedPrefix = STAGING_KEY_ROOT + userId + "/";
        if (s3Key == null || !s3Key.startsWith(expectedPrefix) || s3Key.contains("..")) {
            log.warn("Rejected upload with foreign or malformed storage key: userId={}, s3Key={}", userId, s3Key);
            throw new AudioBusinessException(AudioErrorCode.UNAUTHORIZED_ACCESS);
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

}
