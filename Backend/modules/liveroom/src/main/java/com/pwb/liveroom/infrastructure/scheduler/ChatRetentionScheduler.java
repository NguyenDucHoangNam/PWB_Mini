package com.pwb.liveroom.infrastructure.scheduler;

import com.pwb.liveroom.domain.repository.ChatMessageRepository;
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
public class ChatRetentionScheduler {

    private final ChatMessageRepository chatMessageRepository;
    private final LiveroomConfig config;

    @Scheduled(cron = "${pwb.liveroom.scheduler.chat-retention-cron:0 0 4 * * *}")
    @Transactional
    public void purgeExpiredMessages() {
        Instant threshold = Instant.now().minus(config.getChat().getRetention());
        int purged = chatMessageRepository.deleteSentBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} chat message(s) older than {}", purged, threshold);
        }
    }
}