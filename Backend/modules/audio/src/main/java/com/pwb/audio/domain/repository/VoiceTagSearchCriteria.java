package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.enums.VoiceTagType;

import java.util.UUID;

/**
 * @param userId  the caller; never null. See {@link SongSearchCriteria} on why this is not optional
 * @param keyword free text matched against the name only, never against the synthesis source text
 */
public record VoiceTagSearchCriteria(
        UUID userId,
        String keyword,
        VoiceTagType tagType,
        String languageCode
) {

    public VoiceTagSearchCriteria {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        keyword = (keyword == null) ? null : keyword.trim();
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
