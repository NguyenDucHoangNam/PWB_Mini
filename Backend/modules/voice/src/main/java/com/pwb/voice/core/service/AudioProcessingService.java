package com.pwb.voice.core.service;

import com.pwb.voice.core.model.SongTagConfig;

import java.nio.file.Path;

public interface AudioProcessingService {

    AudioMetadata extractMetadata(Path audioFile);

    Path insertVoiceTagAtInterval(Path original, Path voiceTag, Path output, SongTagConfig config);
}