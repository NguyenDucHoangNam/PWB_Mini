package com.pwb.backend.audio.internal.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.audio.internal.domain.enums.DemoStatus;
import com.pwb.backend.audio.internal.domain.enums.JobStatus;
import com.pwb.backend.audio.api.event.AudioProcessingEvent;
import com.pwb.backend.audio.internal.application.helper.AesKeyManager;
import com.pwb.backend.audio.internal.application.helper.FfmpegExecutor;
import com.pwb.backend.audio.internal.application.helper.FfmpegProbeResult;
import com.pwb.backend.audio.internal.application.helper.S3KeyBuilder;
import com.pwb.backend.audio.internal.application.helper.WaveformExtractor;
import com.pwb.backend.audio.internal.domain.model.AudioProcessingJob;
import com.pwb.backend.audio.internal.domain.model.Demo;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioProcessingJobRepository;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.storage.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioProcessingWorker {

  private final AudioProperties audioProperties;
  private final DemoRepository demoRepository;
  private final AudioProcessingJobRepository jobRepository;
  private final FfmpegExecutor ffmpegExecutor;
  private final WaveformExtractor waveformExtractor;
  private final AesKeyManager aesKeyManager;
  private final S3KeyBuilder s3KeyBuilder;
  private final StorageService storageService;
  private final WebSocketNotificationService webSocketNotificationService;
  private final ObjectMapper objectMapper;
  private final WorkerJobStateUpdater jobUpdater;

  private Semaphore concurrencySemaphore;

  @Value("${spring.kafka.consumer.group-id.audio:pwb-audio-worker}")
  private String workerGroupId;

  @PostConstruct
  void initSemaphore() {
    int cap = Math.max(1, audioProperties.getWorker().getConcurrencyCap());
    this.concurrencySemaphore = new Semaphore(cap);
    log.info("AudioProcessingWorker concurrency cap initialized to {}", cap);
  }

  @KafkaListener(
      topics = "audio-processing-events",
      groupId = "${spring.kafka.consumer.group-id.audio:pwb-audio-worker}",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void consume(@Payload String payload,
                      @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                      Acknowledgment ack) {
    AudioProcessingEvent event;
    try {
      event = objectMapper.readValue(payload, AudioProcessingEvent.class);
    } catch (Exception ex) {
      log.error("Malformed event payload, sending to DLQ: {}", payload, ex);
      ack.acknowledge();
      return;
    }
    log.info("AudioProcessingWorker received: demoId={}, s3Key={}", event.demoId(), event.s3Key());
    try {
      acquirePermit();
      process(event);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("Worker interrupted for demoId={}", event.demoId());
    } catch (Exception ex) {
      log.error("Unexpected error processing demo={}", event.demoId(), ex);
      markFailed(event.demoId(), "INTERNAL_WORKER_ERROR: " + ex.getMessage());
      webSocketNotificationService.sendProcessingFailed(
          event.userId(), event.demoId(), ex.getMessage());
    } finally {
      releasePermit();
      ack.acknowledge();
    }
  }

  private void acquirePermit() throws InterruptedException {
    concurrencySemaphore.acquire();
  }

  private void releasePermit() {
    try {
      concurrencySemaphore.release();
    } catch (Exception ignored) {
    }
  }

  private void process(AudioProcessingEvent event) {
    Demo demo = demoRepository.findByIdAndDeletedFalse(event.demoId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
            "Demo not found"));
    AudioProcessingJob job = jobRepository.findByDemoIdAndDeletedFalse(demo.getId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
            "Job not found"));

    jobUpdater.markJobRunning(job);
    markDemoStatus(demo, DemoStatus.PROCESSING, null);

    Path workDir = ensureWorkDir(demo.getId());
    Path inputPath = workDir.resolve("input." + extractExtension(event.s3Key()));
    try {
      downloadInput(event.s3Key(), inputPath);

      FfmpegProbeResult probe = ffmpegExecutor.probe(inputPath.toString());
      com.pwb.backend.audio.internal.application.helper.FfmpegValidator.validate(probe);

      byte[] demoKey = aesKeyManager.generateDemoKey();
      byte[] encrypted = aesKeyManager.encryptMaster(demoKey);
      int version = audioProperties.getAes().getKeyVersion();
      aesKeyManager.cacheInRedis(demo.getId(), demoKey, version);

      Path hlsOutputDir = workDir.resolve("hls");
      Files.createDirectories(hlsOutputDir);

      String keyHex = aesKeyManager.toHex(demoKey);
      ffmpegExecutor.segmentToHls(inputPath.toString(), hlsOutputDir.toString(),
          keyHex.getBytes(),
          audioProperties.getStream().getHls().getSegmentDurationSeconds());

      uploadHlsArtifacts(demo.getId(), hlsOutputDir);

      List<Float> waveform = waveformExtractor.extract(inputPath.toString(), 200);

      finalizeDemoActive(demo, encrypted, version, waveform,
          probe.durationSeconds(), probe.sampleRate(), extractExtension(event.s3Key()),
          "stream/" + demo.getId() + "/playlist.m3u8");

      jobUpdater.markJobCompleted(job);

      webSocketNotificationService.sendProcessingCompleted(
          event.userId(), demo.getId(), waveform);

      log.info("Demo {} processed successfully", demo.getId());
    } catch (BusinessException ex) {
      log.warn("Demo {} processing business failure", demo.getId(), ex);
      cleanupFailedDemo(demo, ex.getMessage());
      jobUpdater.markJobFailed(job, ex.getMessage());
      webSocketNotificationService.sendProcessingFailed(event.userId(), demo.getId(), ex.getMessage());
    } catch (Exception ex) {
      log.error("Demo {} processing exception", demo.getId(), ex);
      cleanupFailedDemo(demo, "FFMPEG_PROCESS_FAILED: " + ex.getMessage());
      jobUpdater.markJobFailed(job, ex.getMessage());
      webSocketNotificationService.sendProcessingFailed(event.userId(), demo.getId(), ex.getMessage());
    } finally {
      deleteRecursively(workDir);
    }
  }

  private void downloadInput(String s3Key, Path target) throws IOException {
    try (InputStream is = storageService.getObjectStream(s3Key)) {
      if (is == null) {
        throw new BusinessException(ErrorCode.FILE_NOT_FOUND_ON_S3, "Uploaded file not found");
      }
      Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void uploadHlsArtifacts(String demoId, Path hlsDir) {
    try (java.util.stream.Stream<Path> stream = Files.list(hlsDir)) {
      List<Path> files = stream.toList();
      for (Path file : files) {
        String fileName = file.getFileName().toString();
        String key = "stream/" + demoId + "/" + fileName;
        try (InputStream is = Files.newInputStream(file)) {
          long size = Files.size(file);
          String contentType = inferContentType(fileName);
          storageService.uploadFile(key, is, size, contentType);
        }
      }
      log.info("Uploaded {} HLS artifacts for demo={}", files.size(), demoId);
    } catch (IOException ex) {
      log.error("Failed to upload HLS artifacts for demo={}", demoId, ex);
      throw new BusinessException(ErrorCode.HLS_SEGMENTATION_FAILED,
          "HLS upload failed: " + ex.getMessage());
    }
  }

  private String inferContentType(String fileName) {
    if (fileName.endsWith(".m3u8")) {
      return "application/vnd.apple.mpegurl";
    }
    if (fileName.endsWith(".ts")) {
      return "video/mp2t";
    }
    return "application/octet-stream";
  }

  private void finalizeDemoActive(Demo demo, byte[] encryptedKey, int version,
                                  List<Float> waveform, double durationSec,
                                  int sampleRate, String format, String hlsKey) {
    demo.setAesKeyEncrypted(encryptedKey);
    demo.setAesKeyVersion(version);
    demo.setWaveformData(waveform);
    demo.setDuration(BigDecimal.valueOf(durationSec));
    demo.setSampleRate(sampleRate);
    demo.setFormat(format);
    demo.setHlsPlaylistS3Key(hlsKey);
    demo.setErrorMessage(null);
    demo.setStatus(DemoStatus.ACTIVE);
    demoRepository.save(demo);
    log.info("Demo {} marked ACTIVE with HLS={}", demo.getId(), hlsKey);
  }

  private void markDemoStatus(Demo demo, DemoStatus status, String errorMessage) {
    demo.setStatus(status);
    demo.setErrorMessage(errorMessage);
    demoRepository.save(demo);
  }

  private void cleanupFailedDemo(Demo demo, String errorMessage) {
    demo.setStatus(DemoStatus.FAILED);
    demo.setErrorMessage(errorMessage);
    demoRepository.save(demo);
    if (demo.getConfirmedS3Key() != null) {
      try {
        storageService.deleteFile(demo.getConfirmedS3Key());
      } catch (Exception ex) {
        log.warn("Failed to delete confirmed S3 key during cleanup", ex);
      }
    }
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void markJobRunning(AudioProcessingJob job) {
    jobUpdater.markJobRunning(job);
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void markJobCompleted(AudioProcessingJob job) {
    jobUpdater.markJobCompleted(job);
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void markJobFailed(AudioProcessingJob job, String error) {
    jobUpdater.markJobFailed(job, error);
  }

  private void markFailed(String demoId, String reason) {
    demoRepository.findByIdAndDeletedFalse(demoId).ifPresent(d -> {
      d.setStatus(DemoStatus.FAILED);
      d.setErrorMessage(reason);
      demoRepository.save(d);
    });
  }

  private Path ensureWorkDir(String demoId) {
    try {
      Path base = Paths.get(audioProperties.getWorker().getTempDir(), demoId);
      Files.createDirectories(base);
      return base;
    } catch (IOException ex) {
      log.error("Failed to create work dir", ex);
      throw new BusinessException(ErrorCode.FFMPEG_PROCESS_FAILED,
          "Failed to create work dir");
    }
  }

  private void deleteRecursively(Path dir) {
    try {
      if (!Files.exists(dir)) {
        return;
      }
      try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
        stream.sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> {
              try {
                Files.deleteIfExists(p);
              } catch (IOException ignored) {
              }
            });
      }
    } catch (IOException ex) {
      log.warn("Failed to delete work dir {}", dir, ex);
    }
  }

  private String extractExtension(String s3Key) {
    int dot = s3Key.lastIndexOf('.');
    if (dot < 0) {
      return "bin";
    }
    return s3Key.substring(dot + 1);
  }
}
