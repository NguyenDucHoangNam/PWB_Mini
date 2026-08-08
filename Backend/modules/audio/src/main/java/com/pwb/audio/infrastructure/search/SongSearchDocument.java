package com.pwb.audio.infrastructure.search;

import com.pwb.audio.domain.model.Song;

import java.time.Instant;
import java.util.UUID;

/**
 * What a song looks like inside the index. Deliberately narrower than the row: only {@code title} is
 * searchable, and the rest is what the filters need. Artist, album, storage keys and the last error are
 * left out — nothing queries them, and an index that mirrors the table is an index that has to be
 * rebuilt every time an unrelated column changes.
 */
public record SongSearchDocument(
        UUID id,
        UUID userId,
        String title,
        String status,
        String format,
        Integer durationSeconds,
        Instant createdAt
) {

    public static SongSearchDocument from(Song song) {
        return new SongSearchDocument(
                song.getId(),
                song.getUserId(),
                song.getTitle(),
                song.getStatus() == null ? null : song.getStatus().name(),
                song.getFormat() == null ? null : song.getFormat().value(),
                song.getDurationSeconds(),
                song.getCreatedAt()
        );
    }
}
