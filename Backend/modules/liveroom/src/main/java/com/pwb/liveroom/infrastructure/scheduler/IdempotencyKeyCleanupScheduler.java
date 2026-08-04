package com.pwb.liveroom.infrastructure.scheduler;

import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupScheduler {

    private final JoinRequestRepository joinRequestRepository;
    private final LiveroomConfig config;

    @Scheduled(cron = "${pwb.liveroom.scheduler.idempotency-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void releaseExpiredKeys() {
        Instant threshold = Instant.now().minus(config.getRoom().getIdempotencyKeyTtl());
        int released = joinRequestRepository.expireIdempotencyKeys(threshold);
        if (released > 0) {
            log.info("Released {} expired join request idempotency key(s)", released);
        }
    }
}