package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.command.VoiceTagAudioUpload;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.support.VoiceTagViews;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.TtsPreview;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.model.vo.AudioFormat;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.PresignedUrl;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.domain.service.TtsPreviewCache;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.domain.service.TtsVoice;
import com.pwb.audio.infrastructure.audio.AudioProbeService;
import com.pwb.audio.infrastructure.audio.properties.VoiceTagUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagUseCaseImpl implements VoiceTagUseCase {

    private final VoiceTagRepository voiceTagRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final StoragePort storagePort;
    private final StorageCleaner storageCleaner;
    private final TextToSpeechPort textToSpeechPort;
    private final TtsPreviewCache ttsPreviewCache;
    private final AudioProbeService audioProbe;
    private final VoiceTagUploadProperties voiceTagUploadProperties;

    /**
     * Deliberately not transactional: synthesis calls out to Google and then uploads to object storage,
     * so wrapping it would pin a database connection for the whole round trip. The single write at the end
     * gets its own transaction, and the uploaded object is reclaimed if that write fails.
     */
    @Override
    public VoiceTagView createVoiceTagTts(UUID userId, String name, String text, String languageCode, String voiceName) {
        log.info("Creating TTS voice tag: userId={}, name={}, voice={}", userId, name, voiceName);

        assertVoiceAvailable(languageCode, voiceName);

        if (voiceTagRepository.existsByUserIdAndName(userId, name)) {
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }

        TtsSynthesisOutcome outcome = synthesizeAndUploadTts(userId, name, text, languageCode, voiceName);

        VoiceTag saved;
        try {
            saved = voiceTagRepository.save(VoiceTag.createTtsTag(
                    userId,
                    name,
                    text,
                    languageCode,
                    voiceName,
                    outcome.s3Key(),
                    outcome.durationSeconds(),
                    outcome.fileSizeBytes()
            ));
        } catch (DataIntegrityViolationException ex) {
            // The name check above is not atomic; the unique constraint is what actually decides.
            storageCleaner.deleteNow(outcome.s3Key());
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        } catch (RuntimeException ex) {
            storageCleaner.deleteNow(outcome.s3Key());
            throw ex;
        }

        log.info("TTS voice tag created: voiceTagId={}", saved.getId());
        return toVoiceTagView(saved);
    }

    /**
     * Not transactional for the same reason as the TTS path: probing and uploading are slow, and the one
     * write at the end gets its own transaction with the stored object reclaimed if it fails.
     */
    @Override
    public VoiceTagView createVoiceTagUpload(UUID userId, String name, VoiceTagAudioUpload upload) {
        log.info("Creating uploaded voice tag: userId={}, name={}, bytes={}", userId, name, upload.sizeBytes());

        AudioFormat format = requireSupportedFormat(upload);
        requireSizeWithinLimit(upload);
        int durationSeconds = requireDurationWithinLimit(upload, format);

        if (voiceTagRepository.existsByUserIdAndName(userId, name)) {
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }

        String s3Key = buildUploadKey(userId, name, format);
        String uploadedKey;
        try {
            uploadedKey = storagePort.uploadBytes(s3Key, upload.content(), contentTypeFor(format));
        } catch (Exception ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }

        VoiceTag saved;
        try {
            saved = voiceTagRepository.save(VoiceTag.createUploadedTag(
                    userId, name, uploadedKey, durationSeconds, upload.sizeBytes()));
        } catch (DataIntegrityViolationException ex) {
            // The name check above is not atomic; the unique constraint is what actually decides.
            storageCleaner.deleteNow(uploadedKey);
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        } catch (RuntimeException ex) {
            storageCleaner.deleteNow(uploadedKey);
            throw ex;
        }

        log.info("Uploaded voice tag created: voiceTagId={}, duration={}s", saved.getId(), durationSeconds);
        return toVoiceTagView(saved);
    }

    /**
     * Not transactional, and still writes nothing to object storage or the database: a preview exists so
     * the user can reject it, and rejected audio must leave no permanent trace.
     *
     * <p>The bytes do now reach Redis under a TTL, which narrows that rule rather than keeping it whole.
     * It buys the one saving available here: every synthesis is a billed Google call, and choosing a voice
     * means replaying the same phrase across several of them, so the second play onwards is free. Rejected
     * audio therefore outlives its rejection by {@code pwb.audio.tts.preview-cache.ttl} and then expires
     * on its own. Set {@code pwb.audio.tts.preview-cache.enabled: false} to get the original behaviour.
     */
    @Override
    public TtsPreview previewVoiceTagTts(String text, String languageCode, String voiceName) {
        assertVoiceAvailable(languageCode, voiceName);

        TtsRequest request = new TtsRequest(text, languageCode, voiceName);
        TtsResult result = ttsPreviewCache.find(request).orElseGet(() -> {
            TtsResult synthesised = textToSpeechPort.synthesize(request);
            ttsPreviewCache.put(request, synthesised);
            log.debug("TTS preview synthesised: language={}, voice={}, bytes={}",
                    languageCode, voiceName, synthesised.audioBytes().length);
            return synthesised;
        });

        return new TtsPreview(result.audioBytes(), result.contentType(), result.durationSeconds());
    }

    @Override
    public List<TtsVoice> listAvailableVoices() {
        return textToSpeechPort.availableVoices();
    }

    @Override
    @Transactional
    public VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command) {
        VoiceTag voiceTag = requireOwnedVoiceTag(command.userId(), command.voiceTagId());

        if (!voiceTag.getName().equals(command.name())
                && voiceTagRepository.existsByUserIdAndNameAndIdNot(command.userId(), command.name(), command.voiceTagId())) {
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }

        voiceTag.updateMetadata(command.name());
        VoiceTag saved = voiceTagRepository.save(voiceTag);

        log.info("Voice tag updated: voiceTagId={}", saved.getId());
        return toVoiceTagView(saved);
    }

    @Override
    @Transactional
    public void deleteVoiceTag(DeleteVoiceTagCommand command) {
        VoiceTag voiceTag = requireOwnedVoiceTag(command.userId(), command.voiceTagId());

        if (songTagConfigRepository.existsByVoiceTagId(command.voiceTagId())) {
            throw new AudioBusinessException(AudioErrorCode.VOICE_TAG_IN_USE);
        }

        voiceTagRepository.deleteById(voiceTag.getId());
        storageCleaner.deleteAfterCommit(voiceTag.getS3Key());

        log.info("Voice tag deleted: voiceTagId={}", voiceTag.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable) {
        return voiceTagRepository.findAllByUserId(userId, pageable)
                .map(this::toVoiceTagView);
    }

    @Override
    @Transactional(readOnly = true)
    public AudioUrlView getVoiceTagAudioUrl(UUID userId, UUID voiceTagId, Duration expiration) {
        VoiceTag voiceTag = requireOwnedVoiceTag(userId, voiceTagId);

        PresignedUrl presigned = storagePort.presignDownload(voiceTag.getS3Key(), expiration);
        return new AudioUrlView(presigned.url(), presigned.expiresAt());
    }

    private VoiceTag requireOwnedVoiceTag(UUID userId, UUID voiceTagId) {
        return voiceTagRepository.findByIdAndUserId(voiceTagId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));
    }

    /**
     * A voice belongs to exactly one language, so accepting one that does not match would either fail at
     * the provider or quietly produce audio in the wrong language. Null means "use the provider default".
     */
    private void assertVoiceAvailable(String languageCode, String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return;
        }
        boolean offered = textToSpeechPort.availableVoices(languageCode).stream()
                .anyMatch(voice -> voice.name().equals(voiceName));
        if (!offered) {
            log.warn("Rejected unsupported TTS voice: language={}, voice={}", languageCode, voiceName);
            throw new AudioBusinessException(AudioErrorCode.TTS_VOICE_NOT_SUPPORTED);
        }
    }

    private TtsSynthesisOutcome synthesizeAndUploadTts(
            UUID userId, String name, String text, String languageCode, String voiceName) {
        TtsResult ttsResult = textToSpeechPort.synthesize(new TtsRequest(text, languageCode, voiceName));
        String s3Key = buildTtsKey(userId, name);

        String uploadedKey;
        try {
            uploadedKey = storagePort.uploadBytes(s3Key, ttsResult.audioBytes(), ttsResult.contentType());
        } catch (Exception ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }

        return new TtsSynthesisOutcome(
                uploadedKey,
                ttsResult.durationSeconds(),
                (long) ttsResult.audioBytes().length
        );
    }

    private VoiceTagView toVoiceTagView(VoiceTag voiceTag) {
        return VoiceTagViews.toView(voiceTag);
    }

    private String buildTtsKey(UUID userId, String name) {
        return String.format("audio/voice-tags/%s/tts-%s-%s.mp3", userId, safeName(name), UUID.randomUUID());
    }

    private String buildUploadKey(UUID userId, String name, AudioFormat format) {
        return String.format("audio/voice-tags/%s/upload-%s-%s.%s",
                userId, safeName(name), UUID.randomUUID(), format.value());
    }

    /**
     * Runs of dots are collapsed as well as the obvious characters. A single dot is harmless in a key, but
     * two adjacent ones make the whole key look like traversal and the storage layer rejects it outright —
     * on the TTS path that rejection lands after the billed synthesis has already happened, so an ordinary
     * name like {@code "demo..v2"} cost a Google call and returned a storage error.
     */
    private String safeName(String name) {
        if (name == null) {
            return UUID.randomUUID().toString();
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("\\.{2,}", ".");
        return sanitized.isBlank() ? UUID.randomUUID().toString() : sanitized;
    }

    /**
     * The extension decides the format, but ffprobe is what proves the bytes are really audio — the check
     * below would otherwise pass for anything renamed to {@code .mp3}.
     */
    private AudioFormat requireSupportedFormat(VoiceTagAudioUpload upload) {
        String fileName = upload.fileName();
        int dot = (fileName == null) ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new AudioBusinessException(AudioErrorCode.UNSUPPORTED_FORMAT);
        }
        try {
            return AudioFormat.of(fileName.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            throw new AudioBusinessException(AudioErrorCode.UNSUPPORTED_FORMAT);
        }
    }

    private void requireSizeWithinLimit(VoiceTagAudioUpload upload) {
        if (upload.sizeBytes() == 0) {
            throw new AudioBusinessException(AudioErrorCode.FILE_EMPTY);
        }
        if (upload.sizeBytes() > voiceTagUploadProperties.getMaxFileSizeBytes()) {
            log.warn("Rejected oversized voice tag upload: size={}, limit={}",
                    upload.sizeBytes(), voiceTagUploadProperties.getMaxFileSizeBytes());
            throw new AudioBusinessException(AudioErrorCode.FILE_TOO_LARGE);
        }
    }

    /**
     * Measured here rather than taken from the request: the client cannot be trusted about it, and a clip
     * longer than the limit would be stamped over a song at an interval it no longer fits inside.
     */
    private int requireDurationWithinLimit(VoiceTagAudioUpload upload, AudioFormat format) {
        Double exact;
        try {
            exact = audioProbe.probeExactDurationFromBytes(upload.content(), "." + format.value());
        } catch (AudioBusinessException ex) {
            // ffprobe refusing the bytes means this is not the audio file it claims to be.
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE, ex);
        }

        if (exact == null || exact <= 0) {
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE);
        }
        if (exact > voiceTagUploadProperties.getMaxDurationSeconds()) {
            log.warn("Rejected over-long voice tag upload: duration={}s, limit={}s",
                    exact, voiceTagUploadProperties.getMaxDurationSeconds());
            throw new AudioBusinessException(AudioErrorCode.VOICE_TAG_TOO_LONG);
        }
        // Round up: a 0.4s clip is not a zero-second clip, and the column only holds whole seconds.
        return Math.max(1, (int) Math.ceil(exact));
    }

    private String contentTypeFor(AudioFormat format) {
        return switch (format.value()) {
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            default -> "audio/mpeg";
        };
    }

    private record TtsSynthesisOutcome(String s3Key, Integer durationSeconds, Long fileSizeBytes) {
    }
}
