package com.pwb.audio.domain.model;

import java.util.UUID;

/**
 * @param intervalSeconds           gap between the start of one voice tag and the start of the next
 * @param startOffsetSeconds        where the first voice tag is placed
 * @param voiceTagVolumePercentage  how loud the voice tag itself plays
 * @param duckingPercentage         volume the song keeps while a tag plays; 100 leaves the song untouched
 */
public record AudioProcessingRequest(
        UUID songId,
        String inputKey,
        String voiceTagKey,
        int intervalSeconds,
        int startOffsetSeconds,
        int voiceTagVolumePercentage,
        int duckingPercentage,
        String outputKey
) {
}
