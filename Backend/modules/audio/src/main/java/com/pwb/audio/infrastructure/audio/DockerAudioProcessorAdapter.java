package com.pwb.audio.infrastructure.audio;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.AudioProcessingResult;
import com.pwb.audio.domain.service.AudioProcessorPort;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import com.pwb.audio.domain.service.StoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "pwb.audio.processor.mode", havingValue = "docker")
public class DockerAudioProcessorAdapter implements AudioProcessorPort {

    private static final String CONTAINER_DIR = "/tmp/pwb-audio";

    private final AudioProcessorProperties properties;
    private final StoragePort storagePort;

    public DockerAudioProcessorAdapter(AudioProcessorProperties properties, StoragePort storagePort) {
        this.properties = properties;
        this.storagePort = storagePort;
    }

    @Override
    public AudioProcessingResult embedWatermark(AudioProcessingRequest request) {
        Path localWorkingDir = Paths.get(properties.getWorkingDir()).toAbsolutePath().normalize();
        Path inputFile = null;
        Path voiceTagFile = null;
        Path outputFile = null;

        try {
            Files.createDirectories(localWorkingDir);

            String inputFileName = request.songId() + "-input-" + System.nanoTime() + ".tmp";
            String voiceFileName = request.songId() + "-voice-" + System.nanoTime() + ".tmp";
            String outputFileName = request.songId() + "-output.mp3";

            inputFile = localWorkingDir.resolve(inputFileName);
            voiceTagFile = localWorkingDir.resolve(voiceFileName);
            outputFile = localWorkingDir.resolve(outputFileName);

            downloadToFile(request.inputKey(), inputFile);
            downloadToFile(request.voiceTagKey(), voiceTagFile);

            String containerInputPath = CONTAINER_DIR + "/" + inputFileName;
            String containerVoicePath = CONTAINER_DIR + "/" + voiceFileName;
            String containerOutputPath = CONTAINER_DIR + "/" + outputFileName;

            String filterComplex = buildFilterComplex(
                    request.volumePercentage(),
                    request.fadeInMs(),
                    request.fadeOutMs(),
                    request.startOffsetSeconds());

            int intervalSeconds = Optional.ofNullable(request.intervalSeconds())
                    .orElse(properties.getDefaultIntervalSeconds());

            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("exec");
            command.add(properties.getDockerContainerName());
            command.add("ffmpeg");
            command.add("-y");
            command.add("-i");
            command.add(containerInputPath);
            command.add("-i");
            command.add(containerVoicePath);
            command.add("-filter_complex");
            command.add(filterComplex);
            command.add("-map");
            command.add("[out]");
            command.add(containerOutputPath);

            executeProcess(command);

            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                throw new AudioBusinessException(AudioErrorCode.FFMPEG_EMPTY_OUTPUT);
            }

            long fileSize = Files.size(outputFile);
            Integer duration = probeDuration(containerOutputPath);
            storagePort.uploadFromPath(request.outputKey(), outputFile, fileSize);

            log.info("Watermark embedded via Docker FFmpeg: songId={}, outputKey={}, size={}, duration={}",
                    request.songId(), request.outputKey(), fileSize, duration);

            return new AudioProcessingResult(request.songId(), request.outputKey(), duration, fileSize);
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.error("Docker Watermark processing failed: songId={}", request.songId(), ex);
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        } finally {
            cleanup(inputFile, voiceTagFile, outputFile);
        }
    }

    private void downloadToFile(String s3Key, Path targetPath) {
        try (var in = storagePort.download(s3Key)) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            AudioBusinessException ex2 = new AudioBusinessException(
                    AudioErrorCode.STORAGE_ERROR,
                    "Failed to download object: " + s3Key);
            ex2.initCause(ex);
            throw ex2;
        }
    }

    private String buildFilterComplex(Integer volume, Integer fadeInMs, Integer fadeOutMs, Integer startOffset) {
        int vol = Optional.ofNullable(volume).orElse(properties.getDefaultVolumePercentage());
        int fadeIn = Optional.ofNullable(fadeInMs).orElse(1000);
        int fadeOut = Optional.ofNullable(fadeOutMs).orElse(1000);
        int start = Optional.ofNullable(startOffset).orElse(0);

        double volumeFactor = Math.max(0.0, Math.min(vol, 100)) / 100.0;
        double fadeInSeconds = Math.max(0.0, fadeIn / 1000.0);
        double fadeOutSeconds = Math.max(0.0, fadeOut / 1000.0);

        String voiceChain = "[1:a]volume=" + String.format("%.2f", volumeFactor) +
                ",afade=t=in:st=0:d=" + String.format("%.2f", fadeInSeconds) +
                ",afade=t=out:st=" + (double) start + ":d=" + String.format("%.2f", fadeOutSeconds) +
                "[tag]";

        String mixChain = ";[0:a][tag]amix=inputs=2:duration=first:dropout_transition=0[out]";

        return voiceChain + mixChain;
    }

    private Integer probeDuration(String containerFilePath) {
        try {
            List<String> command = List.of(
                    "docker", "exec", properties.getDockerContainerName(),
                    "ffprobe", "-v", "error",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    containerFilePath
            );
            String output = executeProcessWithOutput(command).trim();
            if (output.isBlank()) return null;
            return (int) Math.round(Double.parseDouble(output));
        } catch (Exception ex) {
            log.warn("Docker FFprobe duration extraction failed", ex);
            return null;
        }
    }

    private void executeProcess(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder logOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, "Docker FFmpeg process timed out");
            }

            if (process.exitValue() != 0) {
                log.error("Docker process execution failed. Output:\n{}", logOutput);
                throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, "Docker FFmpeg returned non-zero exit code: " + process.exitValue());
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        }
    }

    private String executeProcessWithOutput(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(30, TimeUnit.SECONDS);
            return output.toString();
        } catch (Exception ex) {
            log.warn("Failed to execute process with output: {}", command, ex);
            return "";
        }
    }

    private void cleanup(Path... files) {
        if (!Boolean.TRUE.equals(properties.getCleanupOnSuccess())) {
            return;
        }
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ex) {
                    log.debug("Temp file cleanup failed: {}", file, ex);
                }
            }
        }
    }
}
