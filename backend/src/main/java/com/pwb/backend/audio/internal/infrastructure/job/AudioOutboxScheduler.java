package com.pwb.backend.audio.internal.infrastructure.job;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.config.OutboxProperties;
import com.pwb.backend.shared.messaging.outbox.processor.OutboxEventProcessor;
import com.pwb.backend.shared.messaging.outbox.scheduler.AbstractOutboxScheduler;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AudioOutboxScheduler extends AbstractOutboxScheduler<AudioOutboxEvent> {

  public AudioOutboxScheduler(AudioOutboxEventRepository repository,
                              OutboxEventProcessor<AudioOutboxEvent> processor,
                              OutboxService outboxService,
                              OutboxProperties properties) {
    super(repository, processor, outboxService, properties);
  }

  @Override
  protected String moduleName() {
    return "Audio";
  }

  @Override
  protected String lockName() {
    return "audioOutboxScheduler";
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:30000}")
  @SchedulerLock(name = "audioOutboxScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT50S")
  @Transactional
  public void pollAndProcess() {
    super.pollAndProcess();
  }
}
