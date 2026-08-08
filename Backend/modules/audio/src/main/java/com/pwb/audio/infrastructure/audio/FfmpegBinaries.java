package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * Locates the FFmpeg toolchain.
 *
 * <p>Jaffree's {@code atPath(Path)} takes the <em>directory holding</em> the executables and appends the
 * binary name itself — passing the executable there yields lookups like {@code ffmpeg/ffprobe}. Leaving the
 * directory unset lets Jaffree resolve both binaries from {@code PATH}, which is how they are installed in
 * the application image.
 */
@Component
@RequiredArgsConstructor
public class FfmpegBinaries {

    private final AudioProcessorProperties properties;

    public FFmpeg ffmpeg() {
        String dir = properties.getFfmpegDir();
        return isBlank(dir) ? FFmpeg.atPath() : FFmpeg.atPath(Paths.get(dir));
    }

    public FFprobe ffprobe() {
        String dir = properties.getFfmpegDir();
        return isBlank(dir) ? FFprobe.atPath() : FFprobe.atPath(Paths.get(dir));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
