package com.pwb.audio.infrastructure.search;

import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchIndexPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps the audio indices in step with the tables.
 *
 * <p>Called from the repository adapters rather than from use cases, because {@code save} and
 * {@code deleteById} are the only two doors every write goes through — create, rename, the processing
 * state changes and the retry all end there. A use case added later is covered without anyone
 * remembering to wire it up.
 *
 * <p>Both aggregates are hard-deleted, so removal has to be published explicitly; there is no
 * {@code deleted} flag a query could filter on to hide a row that is already gone.
 */
@Component
@RequiredArgsConstructor
public class AudioSearchIndexWriter {

    private final SearchIndexPublisher publisher;

    public void songSaved(Song song) {
        publisher.upsert(SearchIndexNames.SONGS, song.getId().toString(), SongSearchDocument.from(song));
    }

    public void songDeleted(UUID songId) {
        publisher.delete(SearchIndexNames.SONGS, songId.toString());
    }

    public void voiceTagSaved(VoiceTag voiceTag) {
        publisher.upsert(
                SearchIndexNames.VOICE_TAGS,
                voiceTag.getId().toString(),
                VoiceTagSearchDocument.from(voiceTag));
    }

    public void voiceTagDeleted(UUID voiceTagId) {
        publisher.delete(SearchIndexNames.VOICE_TAGS, voiceTagId.toString());
    }
}
