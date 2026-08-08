package com.pwb.audio.domain.service;

import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.AudioProcessingResult;

public interface AudioProcessorPort {

    AudioProcessingResult embedWatermark(AudioProcessingRequest request);
}