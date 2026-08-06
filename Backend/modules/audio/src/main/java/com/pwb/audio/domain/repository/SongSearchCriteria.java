package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.enums.SongStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @param userId   the caller; never null, and never taken from the request — a search that forgets to
 *                 scope by owner returns other people's libraries
 * @param keyword  free text matched against the title only
 * @param statuses empty means every status
 */
public record SongSearchCriteria(
        UUID userId,
        String keyword,
        Collection<SongStatus> statuses,
        String format,
        Integer minDurationSeconds,
        Integer maxDurationSeconds
) {

    public SongSearchCriteria {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        statuses = (statuses == null) ? List.of() : List.copyOf(statuses);
        keyword = (keyword == null) ? null : keyword.trim();
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
