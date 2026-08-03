package com.pwb.audio.infrastructure.audio;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.AudioProcessingResult;
import com.pwb.audio.domain.service.AudioProcessorPort;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs FFmpeg inside an already-running container. This assumes the host's {@code working-dir} is
 * bind-mounted at {@link #CONTAINER_DIR} in that container — without the mount, FFmpeg writes its output
 * where the host cannot see it and the job fails with a misleading "empty output".
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pwb.audio.processor.mode", havingValue = "docker")
public class DockerAudioProcessorAdapter implements AudioProcessorPort {

    private static final String CONTAINER_DIR = "/tmp/pwb-audio";

    private final AudioProcessorProperties properties;
    private final StoragePort storagePort;
    private final AudioWorkspace workspace;

    @Override
    public AudioProcessingResult embedWatermark(AudioProcessingRequest request) {
        Path jobDir = workspace.createJobDirectory(request.songId());
        try {
            Path inputFile = download(request.inputKey(), jobDir.resolve("input"));
            Path voiceTagFile = download(request.voiceTagKey(), jobDir.resolve("voice-tag"));
            Path outputFile = jobDir.resolve("output.mp3");

            String containerInput = toContainerPath(inputFile);
            String containerVoiceTag = toContainerPath(voiceTagFile);
            String containerOutput = toContainerPath(outputFile);

            double songDuration = requireDuration(containerInput, "song");
            double voiceTagDuration = requireDuration(containerVoiceTag, "voice tag");
            assertIntervalFitsTag(request, voiceTagDuration);

            String filterComplex = WatermarkFilterBuilder.build(request, songDuration, voiceTagDuration);
            log.debug("FFmpeg filter for songId={}: {}", request.songId(), filterComplex);

            execute(List.of(
                    "docker", "exec", properties.getDockerContainerName(),
                    "ffmpeg", "-y",
                    "-i", containerInput,
                    "-i", containerVoiceTag,
                    "-filter_complex", filterComplex,
                    "-map", WatermarkFilterBuilder.OUTPUT_LABEL,
                    containerOutput
            ));

            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.error("FFmpeg produced no output visible on the host. Is {} bind-mounted at {} in container {}?",
                        jobDir.getParent(), CONTAINER_DIR, properties.getDockerContainerName());
                throw new AudioBusinessException(AudioErrorCode.FFMPEG_EMPTY_OUTPUT);
            }

            long fileSize = Files.size(outputFile);
            Integer duration = probeDuration(containerOutput);
            storagePort.uploadFromPath(request.outputKey(), outputFile, fileSize);

            log.info("Watermark embedded via Docker FFmpeg: songId={}, outputKey={}, size={}, duration={}",
                    request.songId(), request.outputKey(), fileSize, duration);

            return new AudioProcessingResult(request.songId(), request.outputKey(), duration, fileSize);
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.error("Docker watermark processing failed: songId={}", request.songId(), ex);
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        } finally {
            workspace.release(jobDir);
        }
    }

    private Path download(String storageKey, Path target) {
        try (InputStream in = storagePort.download(storageKey)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    /** Maps a host path under {@code working-dir} onto its counterpart inside the container. */
    private String toContainerPath(Path hostPath) {
        Path root = Paths.get(properties.getWorkingDir()).toAbsolutePath().normalize();
        Path relative = root.relativize(hostPath.toAbsolutePath().normalize());
        return CONTAINER_DIR + "/" + relative.toString().replace('\\', '/');
    }

    private double requireDuration(String containerPath, String what) {
        Integer duration = probeDuration(containerPath);
        if (duration == null || duration <= 0) {
            log.error("Could not read {} duration from {}", what, containerPath);
            throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED);
        }
        return duration;
    }

    private void assertIntervalFitsTag(AudioProcessingRequest request, double voiceTagDuration) {
        if (request.intervalSeconds() <= voiceTagDuration) {
            log.warn("Interval {}s is shorter than the {}s voice tag: songId={}",
                    request.intervalSeconds(), voiceTagDuration, request.songId());
            throw new AudioBusinessException(AudioErrorCode.INVALID_TAG_INTERVAL);
        }
    }

    private Integer probeDuration(String containerPath) {
        try {
            String output = executeWithOutput(List.of(
                    "docker", "exec", properties.getDockerContainerName(),
                    "ffprobe", "-v", "error",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    containerPath
            )).trim();
            return output.isBlank() ? null : (int) Math.round(Double.parseDouble(output));
        } catch (Exception ex) {
            log.warn("Docker FFprobe duration extraction failed for {}: {}", containerPath, ex.getMessage());
            return null;
        }
    }

    private void execute(List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String output = readAll(process);

            if (!process.waitFor(properties.getTimeoutMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, "Docker FFmpeg timed out");
            }
            if (process.exitValue() != 0) {
                log.error("Docker FFmpeg exited with {}. Output:\n{}", process.exitValue(), output);
                throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED,
                        "Docker FFmpeg returned exit code " + process.exitValue());
            }
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        }
    }

    private String executeWithOutput(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        String output = readAll(process);
        process.waitFor(30, TimeUnit.SECONDS);
        return output;
    }

    private String readAll(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }
}
