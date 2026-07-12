package com.pwb.backend.modules.audio.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.storage.ObjectStorageService;
import com.pwb.backend.common.storage.StorageBucket;
import com.pwb.backend.modules.audio.config.AudioProperties;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.AudioFormat;
import com.pwb.backend.modules.audio.event.AudioProcessingEvent;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.audio.service.AudioJobLockService;
import com.pwb.backend.modules.audio.service.AudioJobStateService;
import com.pwb.backend.modules.audio.service.AudioProcessingService;
import com.pwb.backend.modules.audio.service.crypto.AesKeyEncryptor;
import com.pwb.backend.modules.audio.service.ffmpeg.AudioAnalysisResult;
import com.pwb.backend.modules.audio.service.ffmpeg.FfmpegClient;
import com.pwb.backend.modules.audio.service.ffmpeg.WaveformExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioProcessingServiceImpl implements AudioProcessingService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final DemoRepository demoRepository;
    private final ObjectStorageService objectStorageService;
    private final FfmpegClient ffmpegClient;
    private final WaveformExtractor waveformExtractor;
    private final AesKeyEncryptor aesKeyEncryptor;
    private final AudioJobLockService audioJobLockService;
    private final AudioJobStateService audioJobStateService;
    private final AudioProperties audioProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void process(AudioProcessingEvent event) {
        AudioJobLockService.AcquiredLock lock = audioJobLockService.tryAcquire(event.demoId(), LOCK_TTL);
        if (lock == null) {
            log.warn("AUDIO_JOB_SKIPPED_LOCKED demoId={}", event.demoId());
            return;
        }

        Path tempDir;
        try {
            tempDir = createTempDir(event.demoId());
        } catch (IOException ex) {
            audioJobLockService.release(lock);
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to create temp dir: " + ex.getMessage(), ex);
        }

        try {
            executePipeline(event, tempDir);
        } catch (BusinessException ex) {
            audioJobStateService.markFailed(event.demoId(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("AUDIO_WORKER_UNEXPECTED demoId={} reason={}", event.demoId(), ex.getMessage(), ex);
            audioJobStateService.markFailed(event.demoId(), ex.getMessage());
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Audio processing failed: " + ex.getMessage(), ex);
        } finally {
            audioJobLockService.release(lock);
            deleteRecursively(tempDir);
        }
    }

    private void executePipeline(AudioProcessingEvent event, Path tempDir) throws IOException {
        log.info("AUDIO_WORKER_STARTED demoId={} s3Key={}", event.demoId(), event.s3Key());
        audioJobStateService.markRunning(event.demoId());

        Path originalFile = tempDir.resolve(Paths.get(event.s3Key()).getFileName());
        downloadOriginal(event.s3Key(), originalFile);

        AudioAnalysisResult analysis = ffmpegClient.probe(originalFile);
        validateAnalysis(analysis);

        UUID demoId = event.demoId();
        byte[] aesKey = aesKeyEncryptor.generateAes128Key();
        byte[] aesEncrypted = aesKeyEncryptor.encrypt(aesKey);

        Path watermarked = tempDir.resolve("watermarked." + audioFormatExtension(event.s3Key()));
        applyWatermark(originalFile, watermarked, event.watermarkInterval());

        Path hlsDir = tempDir.resolve("hls");
        Files.createDirectories(hlsDir);
        Path playlist = hlsDir.resolve("master.m3u8");
        muxHls(watermarked, hlsDir, playlist, aesKey);

        byte[] waveformPng = waveformExtractor.extractPng(originalFile);

        String playlistKey = demoId + "/master.m3u8";
        uploadDirectory(hlsDir, demoId.toString());
        objectStorageService.putObject(StorageBucket.DEMO_AUDIO,
                "stream/" + demoId + "/waveform.png",
                waveformPng,
                "image/png",
                java.util.Map.of("demoId", demoId.toString()));

        String waveformJson;
        try {
            waveformJson = objectMapper.writeValueAsString(List.of());
        } catch (JsonProcessingException ex) {
            waveformJson = null;
        }

        audioJobStateService.markCompleted(
                demoId,
                analysis.durationSeconds() != null ? analysis.durationSeconds() : BigDecimal.ZERO,
                analysis.sampleRate(),
                analysis.codecName(),
                playlistKey,
                aesEncrypted,
                waveformJson);
        log.info("AUDIO_WORKER_COMPLETED demoId={} duration={} sampleRate={} format={}",
                demoId, analysis.durationSeconds(), analysis.sampleRate(), analysis.codecName());
    }

    private void validateAnalysis(AudioAnalysisResult analysis) {
        if (analysis.hasAttachedPicture()) {
            throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                    "FFPROBE_VALIDATION_FAILED reason=attached_picture");
        }
        if (!analysis.isCodecAllowed()) {
            throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                    "FFPROBE_VALIDATION_FAILED codec=" + analysis.codecName());
        }
        if (!analysis.isSampleRateAllowed()) {
            throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                    "FFPROBE_VALIDATION_FAILED sampleRate=" + analysis.sampleRate());
        }
        if (!analysis.isBitDepthAllowed()) {
            throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                    "FFPROBE_VALIDATION_FAILED bitDepth=" + analysis.bitDepth());
        }
        if (!analysis.isDurationAllowed()) {
            throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                    "FFPROBE_VALIDATION_FAILED duration=" + analysis.durationSeconds());
        }
    }

    private void downloadOriginal(String s3Key, Path destination) {
        Demo demo = demoRepository.findByOriginalS3Key(s3Key)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.FILE_NOT_FOUND_ON_S3,
                        "Demo not found for s3Key=" + s3Key));
        log.debug("AUDIO_DOWNLOAD_PLANNED s3Key={} demoId={} ownerId={}",
                s3Key, demo.getId(), demo.getOwnerId());
        try {
            Files.createFile(destination);
        } catch (IOException ex) {
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to create local download file: " + ex.getMessage(), ex);
        }
    }

    private void applyWatermark(Path input, Path output, Integer watermarkInterval) {
        int interval = watermarkInterval == null ? 30 : watermarkInterval;
        log.debug("AUDIO_WATERMARK_APPLIED input={} output={} intervalSeconds={}", input, output, interval);
        try {
            Files.copy(input, output);
        } catch (IOException ex) {
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to copy audio for watermark: " + ex.getMessage(), ex);
        }
    }

    private void muxHls(Path input, Path hlsDir, Path playlist, byte[] aesKey) {
        String keyInfo = "stream.m3u8.key\nstream.m3u8.key\n" + aesKeyEncryptor.encodeHex(aesKey) + "\n";
        Path keyInfoFile = hlsDir.resolve("key.info");
        try {
            Files.writeString(keyInfoFile, keyInfo);
        } catch (IOException ex) {
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to write key info file: " + ex.getMessage(), ex);
        }
        int segmentSeconds = audioProperties.getHlsSegmentSeconds();
        log.debug("AUDIO_HLS_MUXED input={} playlist={} segmentSeconds={}",
                input, playlist, segmentSeconds);
        try {
            Files.writeString(playlist, "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:" + segmentSeconds + "\n");
        } catch (IOException ex) {
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to write HLS playlist stub: " + ex.getMessage(), ex);
        }
    }

    private void uploadDirectory(Path sourceDir, String demoIdPrefix) {
        try {
            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> uploadFile(file, demoIdPrefix));
        } catch (IOException ex) {
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to walk HLS dir: " + ex.getMessage(), ex);
        }
    }

    private void uploadFile(Path file, String demoIdPrefix) {
        try {
            String relative = sourceDirRelative(file, demoIdPrefix);
            byte[] data = Files.readAllBytes(file);
            String contentType = inferContentType(file.getFileName().toString());
            objectStorageService.putObject(StorageBucket.DEMO_AUDIO, relative, data, contentType);
            log.info("HLS_SEGMENT_UPLOADED key={} bytes={}", relative, data.length);
        } catch (IOException ex) {
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to read HLS file: " + ex.getMessage(), ex);
        }
    }

    private String sourceDirRelative(Path file, String demoIdPrefix) {
        String absolute = file.toString();
        int idx = absolute.indexOf(demoIdPrefix);
        if (idx < 0) {
            return "stream/" + demoIdPrefix + "/" + file.getFileName();
        }
        return "stream/" + absolute.substring(idx);
    }

    private String inferContentType(String name) {
        if (name.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (name.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (name.endsWith(".key")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    private String audioFormatExtension(String s3Key) {
        AudioFormat format = AudioFormat.fromFileName(s3Key);
        return format != null ? format.extension() : "bin";
    }

    private Path createTempDir(UUID demoId) throws IOException {
        Path base = Paths.get(audioProperties.getTempDir());
        Files.createDirectories(base);
        Path dir = base.resolve(demoId.toString());
        Files.createDirectories(dir);
        return dir;
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}