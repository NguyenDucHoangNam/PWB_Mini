package com.pwb.liveroom.infrastructure.scheduler;

import com.pwb.liveroom.application.usecase.AutoEndRoomUseCase;
import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class EmptyRoomScheduler {

    private final LiveRoomRepository liveRoomRepository;
    private final AutoEndRoomUseCase autoEndRoom;
    private final LiveroomConfig config;

    @Scheduled(fixedDelayString = "${pwb.liveroom.scheduler.empty-room-interval-ms:60000}")
    public void endRoomsLeftEmpty() {
        Instant threshold = Instant.now().minus(config.getRoom().getEmptyTimeout());
        List<UUID> roomIds = liveRoomRepository.findEmptyRoomIds(threshold);
        roomIds.forEach(roomId -> {
            try {
                autoEndRoom.execute(roomId, EndedReason.EMPTY_TIMEOUT);
            } catch (Exception ex) {
                log.error("Failed to end empty room {}", roomId, ex);
            }
        });
    }
}