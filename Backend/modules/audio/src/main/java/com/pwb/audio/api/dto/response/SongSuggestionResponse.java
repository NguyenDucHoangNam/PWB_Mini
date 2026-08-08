package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.SongSuggestionView;

import java.util.UUID;

/**
 * A single search-as-you-type row. Only what a dropdown line needs — anything more would be paid for on
 * every keystroke.
 */
public record SongSuggestionResponse(UUID id, String title) {

    public static SongSuggestionResponse from(SongSuggestionView view) {
        return new SongSuggestionResponse(view.id(), view.title());
    }
}
