package com.pwb.audio.infrastructure.audio;

import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the real binaries. Skipped where they are not installed, so it never fails a build for the
 * wrong reason — but on a machine that has them it pins down how they are located, which is where a
 * directory-versus-executable mix-up hides.
 */
@DisplayName("AudioProbeService – reading durations with the real ffprobe")
class AudioProbeServiceTest {

    @TempDir
    Path tempDir;

    private AudioProbeService probeService;

    @BeforeEach
    void setUp() {
        AudioProcessorProperties properties = new AudioProcessorProperties();
        properties.setFfmpegDir("");
        probeService = new AudioProbeService(new FfmpegBinaries(properties));
    }

    @Test
    void finds_ffprobe_on_path_and_reads_the_duration() throws Exception {
        assumeTrue(onPath("ffmpeg"), "ffmpeg not installed");
        assumeTrue(onPath("ffprobe"), "ffprobe not installed");

        Path audio = tempDir.resolve("tone.mp3");
        generateTone(audio, 3);

        assertThat(probeService.probeDuration(audio)).isEqualTo(3);
    }

    @Test
    void reads_the_duration_of_raw_bytes() throws Exception {
        assumeTrue(onPath("ffmpeg"), "ffmpeg not installed");
        assumeTrue(onPath("ffprobe"), "ffprobe not installed");

        Path audio = tempDir.resolve("tone.mp3");
        generateTone(audio, 2);

        assertThat(probeService.probeDurationFromBytes(Files.readAllBytes(audio), ".mp3")).isEqualTo(2);
    }

    private void generateTone(Path target, int seconds) throws IOException, InterruptedException {
        run(List.of("ffmpeg", "-v", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                "-ac", "2", target.toString()));
    }

    private static boolean onPath(String binary) {
        try {
            return run(List.of(binary, "-version")) == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out running " + command);
        }
        return process.exitValue();
    }
}
