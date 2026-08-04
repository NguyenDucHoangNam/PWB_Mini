package com.pwb.audio.api.dto.response;

import com.pwb.audio.domain.enums.TtsVoiceGender;
import com.pwb.audio.domain.service.TtsVoice;

public record TtsVoiceResponse(
        String name,
        String languageCode,
        TtsVoiceGender gender
) {

    public static TtsVoiceResponse from(TtsVoice voice) {
        return new TtsVoiceResponse(voice.name(), voice.languageCode(), voice.gender());
    }
}
