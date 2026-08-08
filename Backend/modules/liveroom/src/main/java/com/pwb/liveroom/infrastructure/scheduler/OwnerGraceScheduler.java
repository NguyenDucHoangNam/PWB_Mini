package com.pwb.liveroom.infrastructure.scheduler;

import com.pwb.liveroom.application.usecase.AutoEndRoomUseCase;
import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerGraceScheduler {

    private final LiveRoomRepository liveRoomRepository;
    private final AutoEndRoomUseCase autoEndRoom;

    @Scheduled(fixedDelayString = "${pwb.liveroom.scheduler.owner-grace-interval-ms:10000}")
    public void endRoomsWhoseOwnerDidNotReturn() {
        Instant now = Instant.now();
        List<LiveRoom> absent = liveRoomRepository.findActiveWithAbsentOwner();
        if (absent.isEmpty()) {
            return;
        }



        absent.stream()
                .filter(room -> room.ownerGraceExpiresAt() != null)
                .filter(room -> now.isAfter(room.ownerGraceExpiresAt()))
                .forEach(room -> {
                    try {
                        autoEndRoom.execute(room.getId(), EndedReason.OWNER_GRACE_EXPIRED);
                    } catch (Exception ex) {

                        log.error("Failed to end room {} after its owner's grace expired", room.getId(), ex);
                    }
                });
    }
}