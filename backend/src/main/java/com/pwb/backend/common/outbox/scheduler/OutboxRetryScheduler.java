package com.pwb.backend.common.outbox.scheduler;

import com.pwb.backend.common.outbox.dispatcher.OutboxDispatcher;
import com.pwb.backend.common.outbox.enums.OutboxStatus;
import com.pwb.backend.common.outbox.model.OutboxEvent;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class OutboxRetryScheduler {

    private final OutboxEventRepository outboxRepository;
    private final OutboxDispatcher dispatcher;

    @Value("${app.outbox.retry-batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.retry-interval-ms}")
    @SchedulerLock(name = "outbox-retry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void retryDueEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> due = outboxRepository.findDueForRetry(
                Set.of(OutboxStatus.PENDING, OutboxStatus.FAILED), now, PageRequest.of(0, batchSize));
        if (due.isEmpty()) {
            return;
        }

        for (OutboxEvent event : due) {
            try {
                dispatcher.markProcessing(event);
                dispatcher.dispatch(event);
            } catch (Exception ex) {
                log.warn("Outbox dispatch failed for event {} eventType {}: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
            }
        }
    }
}
