package com.pwb.backend.audio.internal.config;

import com.pwb.backend.audio.internal.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.outbox.AudioOutboxPublisher;
import com.pwb.backend.audio.internal.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.cdc.CdcEngine;
import com.pwb.backend.shared.outbox.processor.CdcOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@RequiredArgsConstructor
public class AudioCdcConfig {

  @Bean
  public CdcOutboxEventHandler<AudioOutboxEvent> audioCdcOutboxEventHandler(
      AudioOutboxEventRepository repository,
      AudioOutboxPublisher processor) {
    return new CdcOutboxEventHandler<>(repository, processor, "AUDIO_DISTRIBUTION");
  }

  @Bean
  public CdcEngine audioCdcEngine(
      Environment environment,
      CdcOutboxEventHandler<AudioOutboxEvent> handler) {
    return new CdcEngine(
        environment,
        handler::handleEvent,
        "pwb-postgres-connector-audio",
        "public.outbox_events");
  }
}