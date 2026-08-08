package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.VoiceTagSuggestionView;
import com.pwb.audio.domain.enums.VoiceTagType;

import java.util.UUID;

/**
 * A single search-as-you-type row for the voice tag picker. The name is what was matched; the voice,
 * language and type ride along so the picker in the song upload form can show what a tag will sound like
 * without a follow-up request per suggestion. The synthesis source text is not carried — it is not
 * searched, and it is far too long for a dropdown.
 */
public record VoiceTagSuggestionResponse(
        UUID id,
        String name,
        String voiceName,
        String languageCode,
        VoiceTagType tagType
) {

    public static VoiceTagSuggestionResponse from(VoiceTagSuggestionView view) {
        return new VoiceTagSuggestionResponse(
                view.id(),
                view.name(),
                view.voiceName(),
                view.languageCode(),
                view.tagType()
        );
    }
}
