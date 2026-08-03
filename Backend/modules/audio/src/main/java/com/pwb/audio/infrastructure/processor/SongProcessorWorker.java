package com.pwb.audio.infrastructure.processor;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.service.AudioProcessorPort;
import com.pwb.audio.infrastructure.service.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongProcessorWorker {

    private final SongRepository songRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final VoiceTagRepository voiceTagRepository;
    private final AudioProcessorPort audioProcessorPort;
    private final StoragePort storagePort;

    @Transactional
    public void process(UUID songId) {
        log.info("Processing song: songId={}", songId);

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_NOT_FOUND));

        SongTagConfig config = songTagConfigRepository.findBySongId(songId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_TAG_CONFIG_NOT_FOUND));

        VoiceTag voiceTag = voiceTagRepository.findById(config.getVoiceTagId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        String outputKey = buildOutputKey(song.getUserId(), song.getId());

        AudioProcessingRequest request = new AudioProcessingRequest(
                song.getId(),
                song.getOriginalS3Key(),
                voiceTag.getS3Key(),
                config.getIntervalSeconds(),
                config.getVolumePercentage(),
                config.getFadeInDurationMs(),
                config.getFadeOutDurationMs(),
                config.getStartOffsetSeconds(),
                outputKey
        );

        var result = audioProcessorPort.embedWatermark(request);

        song.markProcessed(result.outputKey(), result.durationSeconds());
        songRepository.save(song);

        log.info("Song processed: songId={}, outputKey={}, duration={}",
                song.getId(), result.outputKey(), result.durationSeconds());
    }

    private String buildOutputKey(UUID userId, UUID songId) {
        return String.format("audio/processed/%s/%s.mp3", userId, songId);
    }
}