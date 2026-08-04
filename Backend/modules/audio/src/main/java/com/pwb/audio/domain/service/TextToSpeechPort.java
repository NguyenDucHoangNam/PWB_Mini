package com.pwb.audio.domain.service;

import java.util.List;

public interface TextToSpeechPort {

    TtsResult synthesize(TtsRequest request);

    /**
     * Voices offered for a language, in the order they should be presented.
     *
     * @return empty when the language is not one we offer — never {@code null}
     */
    List<TtsVoice> availableVoices(String languageCode);

    /** Every offered voice across every supported language. */
    List<TtsVoice> availableVoices();
}
