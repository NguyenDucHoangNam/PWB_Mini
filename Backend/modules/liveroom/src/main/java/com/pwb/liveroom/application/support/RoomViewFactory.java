package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.view.RoomLookupView;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RoomViewFactory {

    private final LiveroomConfig config;

    public RoomView toView(LiveRoom room) {
        return new RoomView(
                room.getId(),
                room.getOwnerId(),
                room.getRoomCode().value(),
                room.getRoomCode().display(),
                room.getRoomName().value(),
                room.getStatus(),
                room.getMaxParticipants(),
                room.effectiveMaxParticipants(),
                room.getCurrentParticipantCount(),
                room.getOwnerGraceSeconds(),
                room.isReservedOwnerSlot(),
                room.getOwnerLeftAt(),
                room.getCurrentCycleId(),
                room.getReopenedCount(),
                room.getLastReopenedAt(),
                room.getPreviousEndedAt(),
                room.getEndedAt(),
                room.getEndedReason(),
                room.canUndoEnd(Instant.now(), config.getRoom().getUndoEndWindow()),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }

    public RoomLookupView toLookupView(LiveRoom room) {
        return new RoomLookupView(
                room.getId(),
                room.getRoomCode().value(),
                room.getRoomCode().display(),
                room.getRoomName().value(),
                room.getStatus(),
                room.getMaxParticipants(),
                room.getCurrentParticipantCount(),
                room.getCurrentParticipantCount() >= room.effectiveMaxParticipants()
        );
    }
}