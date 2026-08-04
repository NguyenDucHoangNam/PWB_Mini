package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.enums.RoomStatus;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LiveRoomRepositoryImpl implements LiveRoomRepository {

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomMapper liveRoomMapper;

    @Override
    public LiveRoom save(LiveRoom room) {
        if (room.isNew()) {
            return liveRoomMapper.toDomain(liveRoomJpaRepository.save(liveRoomMapper.toEntity(room)));
        }
        LiveRoomJpaEntity target = loadForUpdate(room.getId());
        liveRoomMapper.applyTo(room, target);
        return liveRoomMapper.toDomain(liveRoomJpaRepository.save(target));
    }

    @Override
    public Optional<LiveRoom> findById(UUID id) {
        return liveRoomJpaRepository.findById(id)
                .map(liveRoomMapper::toDomain);
    }

    @Override
    public Optional<LiveRoom> findByIdForUpdate(UUID id) {
        return liveRoomJpaRepository.findByIdForUpdate(id)
                .map(liveRoomMapper::toDomain);
    }

    @Override
    public Optional<LiveRoom> findByRoomCode(String roomCode) {
        return liveRoomJpaRepository.findByRoomCode(roomCode)
                .map(liveRoomMapper::toDomain);
    }

    @Override
    public boolean existsByRoomCode(String roomCode) {
        return liveRoomJpaRepository.existsByRoomCode(roomCode);
    }

    @Override
    public boolean existsByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName) {
        return liveRoomJpaRepository.existsByOwnerIdAndNormalizedName(ownerId, normalizedName);
    }

    @Override
    public Page<LiveRoom> findAllByOwnerId(UUID ownerId, Pageable pageable) {
        return liveRoomJpaRepository.findAllByOwnerId(ownerId, pageable)
                .map(liveRoomMapper::toDomain);
    }

    @Override
    public Page<LiveRoom> findAllByOwnerIdAndStatus(UUID ownerId, RoomStatus status, Pageable pageable) {
        return liveRoomJpaRepository.findAllByOwnerIdAndStatus(ownerId, status, pageable)
                .map(liveRoomMapper::toDomain);
    }

    @Override
    public List<LiveRoom> findActiveWithAbsentOwner() {
        return liveRoomJpaRepository.findActiveWithAbsentOwner().stream()
                .map(liveRoomMapper::toDomain)
                .toList();
    }

    @Override
    public List<UUID> findEmptyRoomIds(Instant startedBefore) {
        return liveRoomJpaRepository.findEmptyRoomIds(startedBefore);
    }


    private LiveRoomJpaEntity loadForUpdate(UUID id) {
        return liveRoomJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Live room no longer exists: " + id));
    }
}