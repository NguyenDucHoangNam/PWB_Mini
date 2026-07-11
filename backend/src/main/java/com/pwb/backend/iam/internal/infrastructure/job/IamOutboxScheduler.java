package com.pwb.backend.iam.internal.infrastructure.job;

import com.pwb.backend.iam.internal.domain.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.infrastructure.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.config.OutboxProperties;
import com.pwb.backend.shared.messaging.outbox.processor.OutboxEventProcessor;
import com.pwb.backend.shared.messaging.outbox.scheduler.AbstractOutboxScheduler;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IamOutboxScheduler extends AbstractOutboxScheduler<IamOutboxEvent> {

  public IamOutboxScheduler(IamOutboxEventRepository repository,
                             OutboxEventProcessor<IamOutboxEvent> processor,
                             OutboxService outboxService,
                             OutboxProperties properties) {
    super(repository, processor, outboxService, properties);
  }

  @Override
  protected String moduleName() {
    return "IAM";
  }

  @Override
  protected String lockName() {
    return "iamOutboxScheduler";
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:30000}")
  @SchedulerLock(name = "iamOutboxScheduler", lockAtLeastFor = "PT5S", lockAtMostFor = "PT50S")
  @Transactional
  public void pollAndProcess() {
    super.pollAndProcess();
  }
}