package com.pwb.infra.outbox.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs the relay as soon as a writer commits instead of leaving the row to sit until the next poll tick.
 *
 * <p>The poll is what makes the outbox durable — it is the thing that eventually picks up rows written by a
 * process that died, or by an instance whose nudge was dropped. This only removes the wait in the common
 * case, so every failure here is logged and swallowed: the scheduled poll remains the guarantee.
 */
@Slf4j
@Component
public class OutboxRelayTrigger {

    private static final String NUDGE_KEY = OutboxRelayTrigger.class.getName() + ".pending";

    private final ObjectProvider<OutboxRelayScheduler> scheduler;
    private final ExecutorService executor;

    public OutboxRelayTrigger(ObjectProvider<OutboxRelayScheduler> scheduler,
                              @Qualifier("outboxPublishExecutor") ExecutorService executor) {
        this.scheduler = scheduler;
        this.executor = executor;
    }

    /**
     * Publishing before the writer's transaction commits would relay a row that may still roll back, so the
     * nudge waits for the commit. One nudge per transaction is enough however many rows it wrote — the relay
     * claims a whole batch.
     */
    public void requestPublish() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit();
            return;
        }
        if (TransactionSynchronizationManager.hasResource(NUDGE_KEY)) {
            return;
        }

        TransactionSynchronizationManager.bindResource(NUDGE_KEY, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(NUDGE_KEY);
                if (status == STATUS_COMMITTED) {
                    submit();
                }
            }
        });
    }

    private void submit() {
        OutboxRelayScheduler relay = scheduler.getIfAvailable();
        if (relay == null) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    relay.relay();
                } catch (RuntimeException ex) {
                    log.debug("OUTBOX.relay.nudge failed, the scheduled poll will retry: {}", ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            log.debug("OUTBOX.relay.nudge rejected, the scheduled poll will retry");
        }
    }
}
