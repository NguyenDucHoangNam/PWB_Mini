package com.pwb.liveroom.application.support;

import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.RoomSessionCycle;
import com.pwb.liveroom.domain.repository.RoomSessionCycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
@RequiredArgsConstructor
public class SessionCycleStarter {

    private final RoomSessionCycleRepository cycleRepository;


    public RoomSessionCycle open(LiveRoom room, Instant at) {
        int nextNumber = cycleRepository.findHighestCycleNumber(room.getId()) + 1;
        RoomSessionCycle cycle = cycleRepository.save(
                RoomSessionCycle.start(room.getId(), nextNumber, at));
        room.attachCycle(cycle.getId());
        return cycle;
    }


    public void close(LiveRoom room, EndedReason reason, Instant at) {
        cycleRepository.findOpenByRoomId(room.getId()).ifPresent(cycle -> {
            cycle.close(reason, at);
            cycleRepository.save(cycle);
        });
    }


    public void reviveCurrent(LiveRoom room) {
        if (room.getCurrentCycleId() == null) {
            return;
        }
        cycleRepository.findById(room.getCurrentCycleId()).ifPresent(cycle -> {
            cycle.reopenSameCycle();
            cycleRepository.save(cycle);
        });
    }
}