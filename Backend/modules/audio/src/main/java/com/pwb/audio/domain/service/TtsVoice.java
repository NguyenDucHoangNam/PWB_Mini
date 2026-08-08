package com.pwb.audio.domain.service;

import com.pwb.audio.domain.enums.TtsVoiceGender;

public record TtsVoice(
        String name,
        String languageCode,
        TtsVoiceGender gender
) {
}
