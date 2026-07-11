package com.pwb.backend.audio.internal.interfaces.config;

import com.pwb.backend.audio.internal.application.factory.AudioOutboxEventFactory;
import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.publisher.AudioOutboxPublisher;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.cdc.CdcEngine;
import com.pwb.backend.shared.messaging.outbox.processor.CdcOutboxEventHandler;
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
    return new CdcOutboxEventHandler<>(repository, processor, AudioOutboxEventFactory.AGGREGATE_TYPE_AUDIO_DISTRIBUTION);
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