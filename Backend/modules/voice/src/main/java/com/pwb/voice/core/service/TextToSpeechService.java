package com.pwb.voice.core.service;

public interface TextToSpeechService {

    byte[] synthesize(String text, String languageCode);
}
