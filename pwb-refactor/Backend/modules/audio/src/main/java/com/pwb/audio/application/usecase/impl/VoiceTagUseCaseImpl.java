package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.command.*;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.model.TtsRequest;
import com.pwb.audio.domain.model.TtsResult;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.VoiceTagRepository;
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
    public VoiceTagView createVoiceTag(CreateVoiceTagCommand command) {
        log.info("Creating voice tag: userId={}, name={}, type={}",
                command.userId(), command.name(), command.tagType());

        if (voiceTagRepository.existsByUserIdAndName(command.userId(), command.name())) {
            throw new AudioBusinessException(AudioErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }

        VoiceTag voiceTag;
        if (command.tagType() == VoiceTagType.TTS) {
            TtsSynthesisOutcome outcome = synthesizeAndUploadTts(command);
            voiceTag = VoiceTag.createTtsTag(
                    command.userId(),
                    command.name(),
                    command.sourceText(),
                    command.languageCode(),
                    outcome.s3Key(),
                    outcome.durationSeconds()
            );
        } else {
            voiceTag = VoiceTag.createUploadedTag(
                    command.userId(),
                    command.name(),
                    command.s3Key(),
                    command.durationSeconds(),
                    command.fileSizeBytes()
            );
        }

        VoiceTag saved = voiceTagRepository.save(voiceTag);
        log.info("Voice tag created: voiceTagId={}", saved.getId());

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
    public PresignedUrlView getPresignedUploadUrl(UUID userId, String filename, long expirationSeconds) {
        String s3Key = buildUploadKey(userId, filename);
        URL presignedUrl = storagePort.getPresignedUploadUrl(s3Key, expirationSeconds);

        return new PresignedUrlView(null, presignedUrl, expirationSeconds);
    }

    private TtsSynthesisOutcome synthesizeAndUploadTts(CreateVoiceTagCommand command) {
        TtsRequest ttsRequest = new TtsRequest(
                command.sourceText(),
                command.languageCode(),
                null
        );

        TtsResult ttsResult = textToSpeechPort.synthesize(ttsRequest);
        String s3Key = buildTtsKey(command.userId(), command.name());

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

    private String buildUploadKey(UUID userId, String filename) {
        return String.format("audio/voice-tags/%s/%s", userId, filename);
    }

    private String buildTtsKey(UUID userId, String name) {
        String safeName = (name == null) ? UUID.randomUUID().toString() : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("audio/voice-tags/%s/tts-%s-%s.mp3", userId, safeName, UUID.randomUUID());
    }

    private record TtsSynthesisOutcome(String s3Key, Integer durationSeconds) {
    }
}