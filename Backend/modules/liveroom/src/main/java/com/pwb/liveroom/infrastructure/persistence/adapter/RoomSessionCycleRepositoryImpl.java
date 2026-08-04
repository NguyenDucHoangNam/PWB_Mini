package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.model.RoomSessionCycle;
import com.pwb.liveroom.domain.repository.RoomSessionCycleRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.RoomSessionCycleJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.RoomSessionCycleMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.RoomSessionCycleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RoomSessionCycleRepositoryImpl implements RoomSessionCycleRepository {

    private final RoomSessionCycleJpaRepository cycleJpaRepository;
    private final RoomSessionCycleMapper cycleMapper;

    @Override
    public RoomSessionCycle save(RoomSessionCycle cycle) {
        if (cycle.isNew()) {
            return cycleMapper.toDomain(cycleJpaRepository.save(cycleMapper.toEntity(cycle)));
        }
        RoomSessionCycleJpaEntity target = cycleJpaRepository.findById(cycle.getId())
                .orElseThrow(() -> new IllegalStateException("Session cycle no longer exists: " + cycle.getId()));
        cycleMapper.applyTo(cycle, target);
        return cycleMapper.toDomain(cycleJpaRepository.save(target));
    }

    @Override
    public Optional<RoomSessionCycle> findById(UUID id) {
        return cycleJpaRepository.findById(id)
                .map(cycleMapper::toDomain);
    }

    @Override
    public Optional<RoomSessionCycle> findOpenByRoomId(UUID roomId) {
        return cycleJpaRepository.findByRoomIdAndEndedAtIsNull(roomId)
                .map(cycleMapper::toDomain);
    }

    @Override
    public int findHighestCycleNumber(UUID roomId) {
        return cycleJpaRepository.findHighestCycleNumber(roomId);
    }
}