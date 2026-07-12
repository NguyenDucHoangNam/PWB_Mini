package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.audio.event.AudioProcessingEvent;

public interface AudioProcessingService {

    void process(AudioProcessingEvent event);
}