package com.pwb.audio.infrastructure.audio;

import com.pwb.audio.domain.model.AudioProcessingRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WatermarkFilterBuilder {

    public static final String OUTPUT_LABEL = "[out]";

    private static final int SAMPLE_RATE = 44_100;
    private static final int MAX_INSERTIONS = 500;
    private static final int NO_DUCKING = 100;

    private WatermarkFilterBuilder() {
    }

    public static String build(AudioProcessingRequest request,
                               double songDurationSeconds,
                               double voiceTagDurationSeconds) {
        int insertions = countInsertions(request, songDurationSeconds);
        if (insertions == 0) {
            return "[0:a]anull" + OUTPUT_LABEL;
        }

        List<String> chains = new ArrayList<>();
        chains.add(buildTagTrack(request, insertions));
        chains.add(buildSongBed(request, voiceTagDurationSeconds));
        chains.add("[bed][tagtrack]amix=inputs=2:duration=first:normalize=0" + OUTPUT_LABEL);
        return String.join(";", chains);
    }

    private static int countInsertions(AudioProcessingRequest request, double songDurationSeconds) {
        if (request.startOffsetSeconds() >= songDurationSeconds) {
            return 0;
        }
        double remaining = songDurationSeconds - request.startOffsetSeconds();
        int count = (int) Math.floor(remaining / request.intervalSeconds()) + 1;
        return Math.min(count, MAX_INSERTIONS);
    }

    private static String buildTagTrack(AudioProcessingRequest request, int insertions) {
        double tagVolume = request.voiceTagVolumePercentage() / 100.0;
        String prepared = "[1:a]aresample=" + SAMPLE_RATE + ",volume=" + decimal(tagVolume);

        if (insertions == 1) {
            return prepared + "," + delayOf(request, 0) + "[tagtrack]";
        }

        String splitLabels = IntStream.range(0, insertions)
                .mapToObj(i -> "[t" + i + "]")
                .collect(Collectors.joining());
        String delayed = IntStream.range(0, insertions)
                .mapToObj(i -> "[t" + i + "]" + delayOf(request, i) + "[d" + i + "]")
                .collect(Collectors.joining(";"));
        String mixInputs = IntStream.range(0, insertions)
                .mapToObj(i -> "[d" + i + "]")
                .collect(Collectors.joining());

        return prepared + ",asplit=" + insertions + splitLabels
                + ";" + delayed
                + ";" + mixInputs + "amix=inputs=" + insertions + ":duration=longest:normalize=0[tagtrack]";
    }

    private static String delayOf(AudioProcessingRequest request, int index) {
        long delayMs = (long) (request.startOffsetSeconds() + (long) index * request.intervalSeconds()) * 1000L;
        return "adelay=" + delayMs + ":all=1";
    }

    private static String buildSongBed(AudioProcessingRequest request, double voiceTagDurationSeconds) {
        if (request.duckingPercentage() >= NO_DUCKING) {
            return "[0:a]anull[bed]";
        }

        double duckFactor = request.duckingPercentage() / 100.0;
        String start = decimal(request.startOffsetSeconds());
        String interval = decimal(request.intervalSeconds());
        String tagDuration = decimal(voiceTagDurationSeconds);

        String expression = "'if(lt(t," + start + "),1,"
                + "if(lt(mod(t-" + start + "," + interval + ")," + tagDuration + ")," + decimal(duckFactor) + ",1))'";
        return "[0:a]volume=" + expression + ":eval=frame[bed]";
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
