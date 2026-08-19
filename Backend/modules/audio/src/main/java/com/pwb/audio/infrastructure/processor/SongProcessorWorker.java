package com.pwb.audio.infrastructure.processor;

import com.pwb.audio.application.support.StorageCleaner;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.AudioProcessingResult;
import com.pwb.audio.domain.service.AudioProcessorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates song processing. Deliberately not transactional: embedding a watermark means downloading
 * from storage, running FFmpeg and uploading again, which can take minutes — holding a database
 * connection across that would drain the pool under any real load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongProcessorWorker {

    private final SongProcessingTransactions transactions;
    private final AudioProcessorPort audioProcessorPort;
    private final StorageCleaner storageCleaner;

    public void process(UUID songId) {
        log.info("Processing song: songId={}", songId);

        try {
            Optional<AudioProcessingRequest> pending = transactions.loadPending(songId);
            if (pending.isEmpty()) {
                log.info("Song is no longer awaiting processing, skipping: songId={}", songId);
                return;
            }

            AudioProcessingResult result = audioProcessorPort.embedWatermark(pending.get());
            boolean orphaned = transactions.markProcessed(songId, result.outputKey(), result.durationSeconds());
            if (orphaned) {
                storageCleaner.deleteNow(result.outputKey());
                return;
            }

            log.info("Song processed: songId={}, outputKey={}, duration={}",
                    songId, result.outputKey(), result.durationSeconds());
        } catch (RuntimeException ex) {
            recordFailure(songId, ex);
            throw ex;
        }
    }

    /**
     * Recording the failure must never replace the original one in the stack trace, so it swallows its own.
     */
    private void recordFailure(UUID songId, RuntimeException cause) {
        try {
            transactions.markFailed(songId, describe(cause));
        } catch (RuntimeException ex) {
            log.error("Could not record processing failure: songId={}", songId, ex);
        }
    }

    private String describe(RuntimeException ex) {
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }
}
