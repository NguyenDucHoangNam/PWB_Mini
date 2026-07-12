package com.pwb.backend.modules.audio.service.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.modules.audio.config.AudioProperties;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class FfmpegClient {

    private static final long PROCESS_TIMEOUT_SECONDS = 60L;
    private static final String VALIDATION_FAILED_MESSAGE = "FFPROBE_VALIDATION_FAILED";

    private final ObjectMapper objectMapper;
    private final AudioProperties audioProperties;

    public AudioAnalysisResult probe(Path file) {
        List<String> command = buildCommand(
                audioProperties.getFfprobePath(),
                "-v", "error",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                "-i", file.toAbsolutePath().toString());

        ProcessResult result = execute(command, "ffprobe");
        if (result.exitCode() != 0) {
            log.warn("FFPROBE_NONZERO_EXIT code={} stderr={}", result.exitCode(), result.stderr());
            throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                    VALIDATION_FAILED_MESSAGE + " exit=" + result.exitCode());
        }

        try {
            JsonNode root = objectMapper.readTree(result.stdout());
            JsonNode streams = root.path("streams");
            JsonNode audioStream = null;
            for (JsonNode stream : streams) {
                if ("audio".equalsIgnoreCase(stream.path("codec_type").asText())) {
                    audioStream = stream;
                    break;
                }
            }
            if (audioStream == null) {
                throw new BusinessException(AudioErrorCode.INVALID_AUDIO_CONTENT,
                        VALIDATION_FAILED_MESSAGE + " reason=no_audio_stream");
            }

            String codecName = audioStream.path("codec_name").asText(null);
            int sampleRate = audioStream.path("sample_rate").asInt(0);
            int channels = audioStream.path("channels").asInt(0);
            int bitDepth = audioStream.path("bits_per_sample").asInt(0);
            boolean hasAttachedPicture = streams.findValues("disposition").stream()
                    .filter(node -> node.isInt() && node.asInt() != 0)
                    .map(node -> node.toString())
                    .anyMatch(value -> value.contains("attached_pic"));

            String durationStr = root.path("format").path("duration").asText(null);
            BigDecimal duration = durationStr != null ? new BigDecimal(durationStr) : null;
            long fileSize = root.path("format").path("size").asLong(0L);

            return new AudioAnalysisResult(codecName, duration, sampleRate, channels, bitDepth,
                    hasAttachedPicture, fileSize);
        } catch (IOException ex) {
            log.warn("FFPROBE_PARSE_FAILED reason={}", ex.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to parse ffprobe output: " + ex.getMessage(), ex);
        }
    }

    public byte[] extractWaveform(Path input, int peaks) {
        Path output = input.getParent().resolve("waveform.png");
        List<String> command = buildCommand(
                audioProperties.getFfmpegPath(),
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-i", input.toAbsolutePath().toString(),
                "-ac", "1",
                "-filter_complex", "showwavespic=s=" + peaks + "x64:colors=white",
                "-frames:v", "1",
                output.toAbsolutePath().toString());

        ProcessResult result = execute(command, "ffmpeg-waveform");
        if (result.exitCode() != 0) {
            log.warn("FFMPEG_WAVEFORM_FAILED exit={} stderr={}", result.exitCode(), result.stderr());
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "FFmpeg waveform extraction failed: " + result.stderr());
        }
        try {
            return java.nio.file.Files.readAllBytes(output);
        } catch (IOException ex) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to read waveform image: " + ex.getMessage(), ex);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(output);
            } catch (IOException ignored) {
            }
        }
    }

    public ProcessResult execute(List<String> command, String stage) {
        Process process = null;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            process = pb.start();

            Thread stdoutReader = readerThread(process.getInputStream(), stdout);
            Thread stderrReader = readerThread(process.getErrorStream(), stderr);
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            stdoutReader.join(2000);
            stderrReader.join(2000);

            if (!finished) {
                process.destroyForcibly();
                log.warn("FFMPEG_TIMEOUT stage={} command={}", stage, String.join(" ", command));
                throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                        "FFmpeg timed out at stage=" + stage);
            }
            return new ProcessResult(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (IOException ex) {
            log.warn("FFMPEG_IO_FAILURE stage={} reason={}", stage, ex.getMessage());
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "Failed to launch FFmpeg at " + stage + ": " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new BusinessException(AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED,
                    "FFmpeg interrupted at " + stage, ex);
        }
    }

    public List<String> buildCommand(String binary, String... args) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private Thread readerThread(java.io.InputStream input, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (sink) {
                        sink.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
            }
        }, "ffmpeg-reader-" + System.nanoTime());
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}