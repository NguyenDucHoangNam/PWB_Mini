package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.infrastructure.service.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagUseCaseImpl implements VoiceTagUseCase {

    private final VoiceTagRepository voiceTagRepository;
    private final StoragePort storagePort;
    private final TextToSpeechPort textToSpeechPort;

    @Override
    @Transactional
    public VoiceTagView createVoiceTagTts(UUID userId, String name, String text, String languageCode) {
        log.info("Creating TTS voice tag: userId={}, name={}", userId, name);

        if (voiceTagRepository.existsByUserIdAndName(userId, name)) {
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }

        TtsSynthesisOutcome outcome = synthesizeAndUploadTts(userId, name, text, languageCode);
        VoiceTag voiceTag = VoiceTag.createTtsTag(
                userId,
                name,
                text,
                languageCode,
                outcome.s3Key(),
                outcome.durationSeconds()
        );

        VoiceTag saved = voiceTagRepository.save(voiceTag);
        log.info("TTS voice tag created: voiceTagId={}", saved.getId());

        return toVoiceTagView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceTagView getVoiceTag(UUID userId, UUID voiceTagId) {
        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(voiceTagId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));
        return toVoiceTagView(voiceTag);
    }

    @Override
    @Transactional
    public VoiceTagView updateVoiceTag(UpdateVoiceTagCommand command) {
        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(command.voiceTagId(), command.userId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        if (command.name() != null && !command.name().equals(voiceTag.getName())) {
            if (voiceTagRepository.existsByUserIdAndNameAndIdNot(command.userId(), command.name(), command.voiceTagId())) {
                throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
            }
        }

        voiceTag.updateMetadata(command.name());

        if (voiceTag.isTts()) {
            voiceTag.updateTtsParams(command.sourceText(), command.languageCode());
        }

        VoiceTag saved = voiceTagRepository.save(voiceTag);
        log.info("Voice tag updated: voiceTagId={}", saved.getId());

        return toVoiceTagView(saved);
    }

    @Override
    @Transactional
    public void deleteVoiceTag(DeleteVoiceTagCommand command) {
        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(command.voiceTagId(), command.userId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        if (voiceTagRepository.existsByVoiceTagIdInConfig(command.voiceTagId())) {
            throw new AudioBusinessException(AudioErrorCode.VOICE_TAG_IN_USE);
        }

        voiceTag.markDeleted();
        voiceTagRepository.save(voiceTag);

        log.info("Voice tag deleted: voiceTagId={}", voiceTag.getId());
    }

    @Override
    @Transactional
    public VoiceTagView markDefault(UUID userId, UUID voiceTagId) {
        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(voiceTagId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        voiceTag.markDefault();
        VoiceTag saved = voiceTagRepository.save(voiceTag);

        log.info("Voice tag marked as default: voiceTagId={}", saved.getId());
        return toVoiceTagView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoiceTagView> listVoiceTags(UUID userId, Pageable pageable) {
        return voiceTagRepository.findAllByUserId(userId, pageable)
                .map(this::toVoiceTagView);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlView getVoiceTagAudioUrl(UUID userId, UUID voiceTagId, long expirationSeconds) {
        VoiceTag voiceTag = voiceTagRepository.findByIdAndUserId(voiceTagId, userId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        URL presignedUrl = storagePort.getPresignedUrl(voiceTag.getS3Key(), expirationSeconds);
        return new PresignedUrlView(voiceTagId, presignedUrl, expirationSeconds);
    }

    private TtsSynthesisOutcome synthesizeAndUploadTts(UUID userId, String name, String text, String languageCode) {
        TtsRequest ttsRequest = new TtsRequest(
                text,
                languageCode,
                null
        );

        TtsResult ttsResult = textToSpeechPort.synthesize(ttsRequest);
        String s3Key = buildTtsKey(userId, name);

        String uploadedKey;
        try {
            uploadedKey = storagePort.uploadBytes(s3Key, ttsResult.audioBytes(), ttsResult.contentType());
        } catch (Exception ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }

        Integer duration = ttsResult.durationSeconds();
        return new TtsSynthesisOutcome(uploadedKey, duration);
    }

    private VoiceTagView toVoiceTagView(VoiceTag voiceTag) {
        return new VoiceTagView(
                voiceTag.getId(),
                voiceTag.getUserId(),
                voiceTag.getName(),
                voiceTag.getTagType(),
                voiceTag.getSourceText(),
                voiceTag.getLanguageCode(),
                voiceTag.getS3Key(),
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

    private record TtsSynthesisOutcome(String s3Key, Integer durationSeconds) {
    }
}