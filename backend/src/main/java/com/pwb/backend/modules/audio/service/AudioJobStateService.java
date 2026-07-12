package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.audio.entity.AudioProcessingJob;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.AudioJobStatus;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.repository.AudioProcessingJobRepository;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioJobStateService {

    private final DemoRepository demoRepository;
    private final AudioProcessingJobRepository jobRepository;

    @Transactional
    public void markRunning(UUID demoId) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND));
        demo.setStatus(DemoStatus.PROCESSING);
        demoRepository.save(demo);

        AudioProcessingJob job = jobRepository.findByDemoId(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                        "Audio job missing for demoId=" + demoId));
        job.setStatus(AudioJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
    }

    @Transactional
    public void markCompleted(UUID demoId, java.math.BigDecimal duration, Integer sampleRate,
                              String format, String playlistKey, byte[] aesEncrypted,
                              String waveformJson) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND));
        demo.setStatus(DemoStatus.ACTIVE);
        demo.setDuration(duration);
        demo.setSampleRate(sampleRate);
        demo.setFormat(format);
        demo.setHlsPlaylistS3Key(playlistKey);
        demo.setAesKeyEncrypted(aesEncrypted);
        demo.setWaveformData(waveformJson);
        demo.setErrorMessage(null);
        demoRepository.save(demo);

        AudioProcessingJob job = jobRepository.findByDemoId(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED));
        job.setStatus(AudioJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
    }

    @Transactional
    public void markFailed(UUID demoId, String reason) {
        demoRepository.findById(demoId).ifPresent(demo -> {
            demo.setStatus(DemoStatus.FAILED);
            demo.setErrorMessage(truncate(reason));
            demoRepository.save(demo);
        });
        jobRepository.findByDemoId(demoId).ifPresent(job -> {
            job.incrementAttempt(truncate(reason));
            job.setStatus(AudioJobStatus.FAILED);
            job.setLastError(truncate(reason));
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        });
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1024 ? value.substring(0, 1024) : value;
    }
}