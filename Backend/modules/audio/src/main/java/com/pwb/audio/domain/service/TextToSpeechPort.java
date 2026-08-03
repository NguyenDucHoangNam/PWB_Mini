package com.pwb.audio.domain.service;

import com.pwb.audio.domain.model.TtsVoice;

import java.util.List;

public interface TextToSpeechPort {

    TtsResult synthesize(TtsRequest request);

    List<TtsVoice> listVoices(String languageCode);
}