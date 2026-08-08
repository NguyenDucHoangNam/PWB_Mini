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
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the real binaries. Skipped where they are not installed, so it never fails a build for the
 * wrong reason — but on a machine that has them it pins down how they are located, which is where a
 * directory-versus-executable mix-up hides, and whether an {@code ebur128} summary still parses.
 */
@DisplayName("AudioProbeService – measuring audio with the real binaries")
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

    /**
     * The absolute LUFS of a synthetic tone is not worth pinning — it moves with the source filter's own
     * defaults. What has to hold is that the summary is found and parsed at all, and that the number tracks
     * the signal: attenuating the tone by a known amount must move the reading by the same amount.
     */
    @Test
    void reads_the_integrated_loudness_and_tracks_a_known_attenuation() throws Exception {
        assumeTrue(onPath("ffmpeg"), "ffmpeg not installed");

        Path loud = tempDir.resolve("loud.mp3");
        Path quiet = tempDir.resolve("quiet.mp3");
        generateTone(loud, 3);
        generateAttenuatedTone(quiet, 3, 0.1);

        Double loudLufs = probeService.probeLoudnessLufs(loud);
        Double quietLufs = probeService.probeLoudnessLufs(quiet);

        assertThat(loudLufs).isNotNull();
        assertThat(quietLufs).isNotNull();
        // volume=0.1 is -20 dB, and LUFS is a dB scale, so the gap must be the same 20.
        assertThat(loudLufs - quietLufs).isCloseTo(20.0, within(1.0));
    }

    /**
     * Silence does not come back as {@code null} — {@code ebur128} floors it at its -70 LUFS absolute gate,
     * which parses like any other number. Rejecting it is {@link TagLoudnessMatch}'s job, so this only has
     * to prove the reading lands below where that guard sits.
     */
    @Test
    void reports_silence_at_the_absolute_gate() throws Exception {
        assumeTrue(onPath("ffmpeg"), "ffmpeg not installed");

        Path silent = tempDir.resolve("silent.mp3");
        run(List.of("ffmpeg", "-v", "error", "-y",
                "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo:d=3", silent.toString()));

        Double lufs = probeService.probeLoudnessLufs(silent);

        assertThat(lufs).isNotNull().isLessThanOrEqualTo(-60.0);
        assertThat(TagLoudnessMatch.gainDb(-10.0, lufs, 3.0)).isZero();
    }

    private void generateTone(Path target, int seconds) throws IOException, InterruptedException {
        run(List.of("ffmpeg", "-v", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                "-ac", "2", target.toString()));
    }

    private void generateAttenuatedTone(Path target, int seconds, double gain)
            throws IOException, InterruptedException {
        run(List.of("ffmpeg", "-v", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds + ",volume=" + gain,
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
