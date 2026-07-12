package com.pwb.backend.modules.audio.service.ffmpeg;

import com.pwb.backend.modules.audio.config.AudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaveformExtractor {

    private final FfmpegClient ffmpegClient;
    private final AudioProperties audioProperties;

    public byte[] extractPng(Path input) {
        return ffmpegClient.extractWaveform(input, audioProperties.getWaveformPeaks());
    }

    public List<String> defaultArgs(int peaks) {
        return ffmpegClient.buildCommand(
                audioProperties.getFfmpegPath(),
                "-y", "-hide_banner", "-loglevel", "error",
                "-i", "INPUT_PLACEHOLDER",
                "-ac", "1",
                "-filter_complex", "showwavespic=s=" + peaks + "x64:colors=white",
                "-frames:v", "1",
                "OUTPUT_PLACEHOLDER");
    }
}