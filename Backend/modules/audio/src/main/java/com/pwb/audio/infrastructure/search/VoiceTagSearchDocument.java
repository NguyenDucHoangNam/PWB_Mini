package com.pwb.audio.infrastructure.search;

import com.pwb.audio.domain.model.VoiceTag;

import java.time.Instant;
import java.util.UUID;

/**
 * What a voice tag looks like inside the index.
 *
 * <p>{@code sourceText} is intentionally absent. It holds up to 2048 characters of synthesis input and
 * would be the largest field here by far; searching a tag is searching for the name its owner gave it,
 * not for the words it happens to say. The synthesis metadata that is present — voice, language, type —
 * is carried so a suggestion can be rendered without a second round-trip.
 */
public record VoiceTagSearchDocument(
        UUID id,
        UUID userId,
        String name,
        String tagType,
        String languageCode,
        String voiceName,
        Integer durationSeconds,
        boolean isDefault,
        Instant createdAt
) {

    public static VoiceTagSearchDocument from(VoiceTag voiceTag) {
        return new VoiceTagSearchDocument(
                voiceTag.getId(),
                voiceTag.getUserId(),
                voiceTag.getName(),
                voiceTag.getTagType() == null ? null : voiceTag.getTagType().name(),
                voiceTag.getLanguageCode(),
                voiceTag.getVoiceName(),
                voiceTag.getDurationSeconds(),
                voiceTag.isDefault(),
                voiceTag.getCreatedAt()
        );
    }
}
