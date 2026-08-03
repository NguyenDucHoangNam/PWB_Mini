package com.pwb.audio.domain.service;

public interface TextToSpeechPort {

    TtsResult synthesize(TtsRequest request);
}
