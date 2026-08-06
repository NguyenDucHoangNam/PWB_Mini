package com.pwb.audio.application.support;

import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns songs into the shape the API answers with. Shared by the ordinary listing and by search, so both
 * return identical rows — a search result that rendered differently from the same song in the library
 * would look like a different song.
 */
@Component
@RequiredArgsConstructor
public class SongViewFactory {

    private final SongTagConfigRepository songTagConfigRepository;

    public SongView toView(Song song, boolean hasVoiceTag) {
        return new SongView(
                song.getId(),
                song.getUserId(),
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getOriginalS3Key(),
                song.getProcessedS3Key(),
                song.getFileSizeBytes(),
                song.getDurationSeconds(),
                song.getFormat() != null ? song.getFormat().value() : null,
                song.getStatus(),
                song.getThumbnailUrl(),
                song.getLastError(),
                song.isProcessed(),
                hasVoiceTag,
                song.getCreatedAt(),
                song.getUpdatedAt()
        );
    }

    public SongView toView(Song song) {
        return toView(song, resolveTaggedSongIds(List.of(song)).contains(song.getId()));
    }

    public List<SongView> toViews(List<Song> songs) {
        Set<UUID> taggedSongIds = resolveTaggedSongIds(songs);
        return songs.stream()
                .map(song -> toView(song, taggedSongIds.contains(song.getId())))
                .toList();
    }

    /**
     * One query for the whole page rather than one per row. Only the existence of a configuration is
     * needed — a listing says that a song carries a voice tag, not which one.
     */
    public Set<UUID> resolveTaggedSongIds(Collection<Song> songs) {
        if (songs.isEmpty()) {
            return Set.of();
        }
        return songTagConfigRepository.findAllBySongIdIn(songs.stream().map(Song::getId).toList())
                .stream()
                .map(SongTagConfig::getSongId)
                .collect(Collectors.toSet());
    }
}
