package com.pwb.audio.infrastructure.processor;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongProcessingTransactions {

    private final SongRepository songRepository;
    private final SongTagConfigRepository songTagConfigRepository;
    private final VoiceTagRepository voiceTagRepository;

    @Transactional(readOnly = true)
    public Optional<AudioProcessingRequest> loadPending(UUID songId) {
        Optional<Song> found = songRepository.findById(songId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Song song = found.get();
        if (song.getStatus() != SongStatus.PROCESSING) {
            return Optional.empty();
        }

        SongTagConfig config = songTagConfigRepository.findBySongId(songId)
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.SONG_TAG_CONFIG_NOT_FOUND));
        if (!config.isEnabled()) {
            log.info("Voice tag configuration is disabled, skipping: songId={}", songId);
            return Optional.empty();
        }

        VoiceTag voiceTag = voiceTagRepository.findById(config.getVoiceTagId())
                .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND));

        return Optional.of(new AudioProcessingRequest(
                song.getId(),
                song.getOriginalS3Key(),
                voiceTag.getS3Key(),
                config.getIntervalSeconds(),
                config.getStartOffsetSeconds(),
                config.getVolumePercentage(),
                config.getDuckingPercentage(),
                buildOutputKey(song.getUserId(), song.getId())
        ));
    }

    /**
     * @return {@code true} when the object the render just uploaded is now orphaned and the caller must
     *         delete it. That is only the case when the song row is gone: it was deleted while the merge
     *         was still running, and {@code deleteSong} cleaned up its keys at a moment when the merged
     *         key did not exist yet, so nothing will ever reference or find that object again.
     *
     *         <p>A duplicate result is emphatically <em>not</em> such a case. The output key is derived
     *         from the song id, so the second render wrote to the same key the song already points at —
     *         deleting it there would delete the audio that is currently being served.
     */
    @Transactional
    public boolean markProcessed(UUID songId, String outputKey, Integer durationSeconds) {
        Optional<Song> found = songRepository.findById(songId);
        if (found.isEmpty()) {
            log.warn("Song vanished before its result could be stored, discarding render: songId={}, outputKey={}",
                    songId, outputKey);
            return true;
        }

        Song song = found.get();
        if (song.isProcessed()) {
            log.debug("Ignoring duplicate processing result: songId={}", songId);
            return false;
        }

        song.markProcessed(outputKey, durationSeconds);
        songRepository.save(song);
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID songId, String error) {
        songRepository.findById(songId).ifPresent(song -> {
            song.markFailed(error);
            songRepository.save(song);
        });
    }

    private String buildOutputKey(UUID userId, UUID songId) {
        return String.format("audio/processed/%s/%s.mp3", userId, songId);
    }
}
