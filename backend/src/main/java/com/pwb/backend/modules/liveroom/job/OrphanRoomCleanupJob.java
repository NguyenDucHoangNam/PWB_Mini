package com.pwb.backend.modules.liveroom.job;

import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.entity.Room;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import com.pwb.backend.modules.liveroom.service.impl.RoomLifecycleServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OrphanRoomCleanupJob {

    private static final String LOCK_NAME = "liveroom_orphan_cleanup_lock";
    private static final int SCAN_BATCH_SIZE = 200;

    private final RoomLifecycleServiceImpl roomLifecycleServiceImpl;
    private final RoomLifecycleService roomLifecycleService;
    private final LiveRoomProperties properties;

    @Scheduled(cron = "${app.liveroom.orphan-scan-cron:0 */2 * * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void runScheduled() {
        runInternal();
    }

    void runInternal() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(properties.getOrphanThresholdMinutes()));
        List<Room> candidates = roomLifecycleServiceImpl.findOrphanCandidates(threshold, SCAN_BATCH_SIZE);
        if (candidates.isEmpty()) {
            log.debug("ORPHAN_SCAN_EMPTY threshold={}", threshold);
            return;
        }
        int closed = 0;
        for (Room room : candidates) {
            if (roomLifecycleServiceImpl.isRedisStatusKeyMissing(room.getRoomCode())) {
                try {
                    roomLifecycleService.closeRoom(room.getRoomCode());
                    closed++;
                } catch (Exception ex) {
                    log.warn("ORPHAN_CLOSE_FAILED roomCode={} reason={}",
                            room.getRoomCode(), ex.getMessage());
                }
            }
        }
        log.warn("ORPHAN_SCAN_FINISHED scanned={} closed={} thresholdMinutes={}",
                candidates.size(), closed, properties.getOrphanThresholdMinutes());
    }
}