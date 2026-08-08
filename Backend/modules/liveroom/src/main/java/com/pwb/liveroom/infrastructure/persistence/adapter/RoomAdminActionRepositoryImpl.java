package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.model.RoomAdminAction;
import com.pwb.liveroom.domain.repository.RoomAdminActionRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.RoomAdminActionJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.repository.RoomAdminActionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RoomAdminActionRepositoryImpl implements RoomAdminActionRepository {

    private final RoomAdminActionJpaRepository actionJpaRepository;

    @Override
    public RoomAdminAction save(RoomAdminAction action) {
        RoomAdminActionJpaEntity entity = RoomAdminActionJpaEntity.builder()
                .roomId(action.roomId())
                .cycleId(action.cycleId())
                .actorUserId(action.actorUserId())
                .targetUserId(action.targetUserId())
                .actionType(action.actionType())
                .reason(action.reason())
                .createdAt(action.createdAt())
                .build();
        return toDomain(actionJpaRepository.save(entity));
    }

    @Override
    public List<RoomAdminAction> findAllByRoomId(UUID roomId) {
        return actionJpaRepository.findAllByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(RoomAdminActionRepositoryImpl::toDomain)
                .toList();
    }

    private static RoomAdminAction toDomain(RoomAdminActionJpaEntity entity) {
        return new RoomAdminAction(
                entity.getId(),
                entity.getRoomId(),
                entity.getCycleId(),
                entity.getActorUserId(),
                entity.getTargetUserId(),
                entity.getActionType(),
                entity.getReason(),
                entity.getCreatedAt()
        );
    }
}