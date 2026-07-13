package com.pwb.backend.modules.liveroom.job;

import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RoomLifecycleCleanupJob {

    private static final String LOCK_NAME = "liveroom_room_cleanup_timeline_job";
    private static final String LOCK_AT_MOST_FOR = "PT45S";
    private static final String LOCK_AT_LEAST_FOR = "PT5S";
    private static final int SCAN_BATCH_SIZE = 200;

    private final RoomLifecycleService roomLifecycleService;

    @Scheduled(cron = "${app.liveroom.lifecycle-cleanup-cron:0 */1 * * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = LOCK_AT_MOST_FOR, lockAtLeastFor = LOCK_AT_LEAST_FOR)
    public void runScheduled() {
        runInternal();
    }

    void runInternal() {
        long nowMillis = Instant.now().toEpochMilli();
        Set<String> expired;
        try {
            expired = roomLifecycleService.findExpiredCleanupCandidates(nowMillis, SCAN_BATCH_SIZE);
        } catch (Exception ex) {
            log.warn("ROOM_CLEANUP_SCAN_FAILED reason={}", ex.getMessage());
            return;
        }
        if (expired.isEmpty()) {
            log.debug("ROOM_CLEANUP_SCAN_EMPTY nowMillis={}", nowMillis);
            return;
        }

        int closed = 0;
        int skipped = 0;
        for (String roomCode : expired) {
            try {
                if (!roomLifecycleService.isStatusStillInactiveOrEmpty(roomCode)) {
                    log.info("ROOM_CLEANUP_SKIP_ACTIVE roomCode={} reason=host_reconnected", roomCode);
                    skipped++;
                    continue;
                }
                roomLifecycleService.closeRoom(roomCode);
                closed++;
            } catch (Exception ex) {
                log.warn("ROOM_CLEANUP_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
            }
        }
        log.warn("ROOM_CLEANUP_SCAN_FINISHED nowMillis={} expired={} closed={} skipped={}",
                nowMillis, expired.size(), closed, skipped);
    }
}
