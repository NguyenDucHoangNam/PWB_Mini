package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.PresignedUrl;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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

    /**
     * Deliberately not transactional: synthesis calls out to Google and then uploads to object storage,
     * so wrapping it would pin a database connection for the whole round trip. The single write at the end
     * gets its own transaction, and the uploaded object is reclaimed if that write fails.
     */
    @Override
    public VoiceTagView createVoiceTagTts(UUID userId, String name, String text, String languageCode) {
        log.info("Creating TTS voice tag: userId={}, name={}", userId, name);

        if (voiceTagRepository.existsByUserIdAndName(userId, name)) {
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }

        TtsSynthesisOutcome outcome = synthesizeAndUploadTts(userId, name, text, languageCode);

        VoiceTag saved;
        try {
            saved = voiceTagRepository.save(VoiceTag.createTtsTag(
                    userId,
                    name,
                    text,
                    languageCode,
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
        return AudioUrlView.single(presigned.url(), presigned.expiresAt());
    }

    private VoiceTag requireOwnedVoiceTag(UUID userId, UUID voiceTagId) {
        return voiceTagRepository.findByIdAndUserId(voiceTagId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));
    }

    private TtsSynthesisOutcome synthesizeAndUploadTts(UUID userId, String name, String text, String languageCode) {
        TtsResult ttsResult = textToSpeechPort.synthesize(new TtsRequest(text, languageCode, null));
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
        return new VoiceTagView(
                voiceTag.getId(),
                voiceTag.getUserId(),
                voiceTag.getName(),
                voiceTag.getTagType(),
                voiceTag.getSourceText(),
                voiceTag.getLanguageCode(),
                voiceTag.getDurationSeconds(),
                voiceTag.getFileSizeBytes(),
                voiceTag.isDefault(),
                voiceTag.getCreatedAt(),
                voiceTag.getUpdatedAt()
        );
    }

    private String buildTtsKey(UUID userId, String name) {
        String safeName = (name == null) ? UUID.randomUUID().toString() : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("audio/voice-tags/%s/tts-%s-%s.mp3", userId, safeName, UUID.randomUUID());
    }

    private record TtsSynthesisOutcome(String s3Key, Integer durationSeconds, Long fileSizeBytes) {
    }
}
