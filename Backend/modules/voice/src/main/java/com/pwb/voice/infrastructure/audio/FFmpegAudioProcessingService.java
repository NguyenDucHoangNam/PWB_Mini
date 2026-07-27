package com.pwb.voice.infrastructure.audio;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Format;
import com.github.kokorin.jaffree.ffprobe.Stream;
import com.pwb.backend.exception.BusinessException;
import com.pwb.voice.core.exception.VoiceErrorCode;

import com.pwb.voice.core.model.SongTagConfig;
import com.pwb.voice.core.service.AudioMetadata;
import com.pwb.voice.core.service.AudioProcessingService;
import com.pwb.voice.infrastructure.config.AudioProcessingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegAudioProcessingService implements AudioProcessingService {

    private static final String FILTER_DELAY = "adelay";
    private static final String FILTER_VOLUME = "volume";
    private static final String FILTER_AMIX = "amix";
    private static final String FILTER_AFADE = "afade";
    private static final String AMIX_DURATION_FIRST = "first";
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int MAX_INSERTION_POINTS = 200;

    private final AudioProcessingProperties audioProcessingProperties;

    @Override
    public AudioMetadata extractMetadata(Path audioFile) {
        try {
            return probe(audioFile);
        } catch (RuntimeException ex) {
            log.error("Audio probe failed: path={}, error={}", audioFile, ex.getMessage(), ex);
            return AudioMetadata.ofDuration(0);
        }
    }

    @Override
    public Path insertVoiceTagAtInterval(Path original, Path voiceTag, Path output, SongTagConfig config) {
        AudioMetadata originalMeta = probe(original);
        AudioMetadata voiceTagMeta = probe(voiceTag);

        int voiceTagDuration = voiceTagMeta.durationSeconds();
        int maxFadeMs = Math.max(
                config.getFadeInDurationMs() == null ? 0 : config.getFadeInDurationMs(),
                config.getFadeOutDurationMs() == null ? 0 : config.getFadeOutDurationMs()
        );
        int voiceTagWindow = voiceTagDuration + (int) Math.ceil((double) maxFadeMs / MILLIS_PER_SECOND);
        if (config.getIntervalSeconds() <= voiceTagWindow) {
            throw new BusinessException(VoiceErrorCode.INVALID_INTERVAL);
        }

        int intervalSeconds = config.getIntervalSeconds();
        int startOffset = config.getStartOffsetSeconds() == null ? 0 : config.getStartOffsetSeconds();
        int totalDuration = originalMeta.durationSeconds();
        int usableWindow = totalDuration - startOffset - voiceTagWindow;
        int insertionPoints = usableWindow > 0
                ? Math.min(MAX_INSERTION_POINTS, usableWindow / intervalSeconds)
                : 0;

        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(output);

            if (insertionPoints == 0) {
                Files.copy(original, output);
                return output;
            }

            StringBuilder filterComplex = new StringBuilder();
            List<String> mixInputs = new ArrayList<>();
            mixInputs.add("[0:a]");

            if (insertionPoints > 1) {
                filterComplex.append("[1:a]asplit=").append(insertionPoints);
                for (int i = 0; i < insertionPoints; i++) {
                    filterComplex.append("[vtag").append(i).append("]");
                }
                filterComplex.append(";");
            }

            for (int i = 0; i < insertionPoints; i++) {
                long delayMs = (long) (startOffset + i * intervalSeconds) * MILLIS_PER_SECOND;
                String inputLabel = (insertionPoints > 1) ? "[vtag" + i + "]" : "[1:a]";
                String outputLabel = "[tag" + i + "]";

                List<String> filters = new ArrayList<>();
                filters.add(FILTER_VOLUME + "=" + volumeFactor(config));

                if (config.getFadeInDurationMs() != null && config.getFadeInDurationMs() > 0) {
                    filters.add(FILTER_AFADE + "=t=in:st=0:d=" + (config.getFadeInDurationMs() / 1000.0));
                }

                if (delayMs > 0) {
                    filters.add(FILTER_DELAY + "=" + delayMs + ":all=1");
                }

                if (config.getFadeOutDurationMs() != null && config.getFadeOutDurationMs() > 0) {
                    filters.add(FILTER_AFADE + "=t=out:st=0:d=" + (config.getFadeOutDurationMs() / 1000.0));
                }

                filterComplex.append(inputLabel)
                        .append(String.join(",", filters))
                        .append(outputLabel)
                        .append(";");

                mixInputs.add(outputLabel);
            }

            StringBuilder mixExpression = new StringBuilder();
            mixInputs.forEach(mixExpression::append);
            mixExpression.append(FILTER_AMIX)
                    .append("=inputs=").append(mixInputs.size())
                    .append(":duration=").append(AMIX_DURATION_FIRST)
                    .append("[out]");

            String fullFilter = filterComplex.toString() + mixExpression;

            log.info("FFmpeg filter: insertionPoints={}, totalDuration={}, interval={}, startOffset={}, fadeIn={}, fadeOut={}, volume={}, filter={}",
                    insertionPoints, totalDuration, intervalSeconds, startOffset,
                    config.getFadeInDurationMs(), config.getFadeOutDurationMs(), config.getVolumePercentage(), fullFilter);

            FFmpeg.atPath()
                    .addInput(UrlInput.fromPath(original))
                    .addInput(UrlInput.fromPath(voiceTag))
                    .addArguments("-filter_complex", fullFilter)
                    .addArguments("-map", "[out]")
                    .setOverwriteOutput(true)
                    .addOutput(UrlOutput.toPath(output))
                    .execute();

            return output;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Audio processing failed: originalPath={}, tagPath={}", original, voiceTag, ex);
            throw new BusinessException(VoiceErrorCode.AUDIO_PROCESSING_FAILED);
        }
    }

    private double volumeFactor(SongTagConfig config) {
        int percentage = config.getVolumePercentage() == null ? 100 : config.getVolumePercentage();
        return Math.max(0.0, Math.min(percentage, 100)) / 100.0;
    }

    private AudioMetadata probe(Path audioFile) {
        FFprobeResult result = FFprobe.atPath()
                .setShowFormat(true)
                .setShowStreams(true)
                .setInput(audioFile)
                .execute();

        Format format = result.getFormat();
        if (format == null) {
            throw new IllegalStateException("FFprobe returned no format data");
        }

        Double durationSeconds = format.getDuration() == null ? null : format.getDuration().doubleValue();
        if (durationSeconds == null) {
            List<Stream> streams = result.getStreams();
            if (streams != null) {
                for (Stream stream : streams) {
                    if (stream.getDuration() != null) {
                        durationSeconds = stream.getDuration().doubleValue();
                        break;
                    }
                }
            }
        }

        if (durationSeconds == null) {
            throw new IllegalStateException("FFprobe returned no duration");
        }

        int durationInt = (int) Math.round(durationSeconds);
        long bitRate = format.getBitRate() == null ? 0L : format.getBitRate();
        int sampleRate = 0;

        List<Stream> streams = result.getStreams();
        if (streams != null) {
            for (Stream stream : streams) {
                if (stream.getSampleRate() != null) {
                    sampleRate = stream.getSampleRate();
                    break;
                }
            }
        }

        return new AudioMetadata(durationInt, format.getFormatName(), bitRate, sampleRate);
    }
}

