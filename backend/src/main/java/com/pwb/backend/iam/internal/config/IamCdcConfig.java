package com.pwb.backend.iam.internal.config;

import com.pwb.backend.iam.internal.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.publisher.IamOutboxPublisher;
import com.pwb.backend.iam.internal.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.cdc.CdcEngine;
import com.pwb.backend.shared.outbox.processor.CdcOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@RequiredArgsConstructor
public class IamCdcConfig {

  @Bean
  public CdcOutboxEventHandler<IamOutboxEvent> iamCdcOutboxEventHandler(
      IamOutboxEventRepository repository,
      IamOutboxPublisher processor) {
    return new CdcOutboxEventHandler<>(repository, processor, "IAM");
  }

  @Bean
  public CdcEngine iamCdcEngine(
      Environment environment,
      CdcOutboxEventHandler<IamOutboxEvent> handler) {
    return new CdcEngine(
        environment,
        handler::handleEvent,
        "pwb-postgres-connector-iam",
        "public.outbox_events");
  }
}