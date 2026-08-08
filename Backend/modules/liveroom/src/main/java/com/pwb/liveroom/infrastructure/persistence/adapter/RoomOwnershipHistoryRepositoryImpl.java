package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.model.RoomOwnershipHistory;
import com.pwb.liveroom.domain.repository.RoomOwnershipHistoryRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.RoomOwnershipHistoryJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.repository.RoomOwnershipHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RoomOwnershipHistoryRepositoryImpl implements RoomOwnershipHistoryRepository {

    private final RoomOwnershipHistoryJpaRepository historyJpaRepository;

    @Override
    public RoomOwnershipHistory save(RoomOwnershipHistory history) {
        RoomOwnershipHistoryJpaEntity entity = RoomOwnershipHistoryJpaEntity.builder()
                .roomId(history.roomId())
                .ownerUserId(history.ownerUserId())
                .changeType(history.changeType())
                .reason(history.reason())
                .changedAt(history.changedAt())
                .build();
        return toDomain(historyJpaRepository.save(entity));
    }

    @Override
    public List<RoomOwnershipHistory> findAllByRoomId(UUID roomId) {
        return historyJpaRepository.findAllByRoomIdOrderByChangedAtDesc(roomId).stream()
                .map(RoomOwnershipHistoryRepositoryImpl::toDomain)
                .toList();
    }

    private static RoomOwnershipHistory toDomain(RoomOwnershipHistoryJpaEntity entity) {
        return new RoomOwnershipHistory(
                entity.getId(),
                entity.getRoomId(),
                entity.getOwnerUserId(),
                entity.getChangeType(),
                entity.getReason(),
                entity.getChangedAt()
        );
    }
}