package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.repository.JoinRequestRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.JoinRequestJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.JoinRequestMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.JoinRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JoinRequestRepositoryImpl implements JoinRequestRepository {

    private final JoinRequestJpaRepository joinRequestJpaRepository;
    private final JoinRequestMapper joinRequestMapper;

    @Override
    public JoinRequest save(JoinRequest request) {
        return joinRequestMapper.toDomain(joinRequestJpaRepository.save(toManaged(request)));
    }

    @Override
    public List<JoinRequest> saveAll(List<JoinRequest> requests) {
        List<JoinRequestJpaEntity> entities = requests.stream()
                .map(this::toManaged)
                .toList();
        return joinRequestJpaRepository.saveAll(entities).stream()
                .map(joinRequestMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<JoinRequest> findById(UUID id) {
        return joinRequestJpaRepository.findById(id)
                .map(joinRequestMapper::toDomain);
    }

    @Override
    public Optional<JoinRequest> findByRoomIdAndUserIdAndIdempotencyKey(
            UUID roomId, UUID userId, String idempotencyKey) {
        return joinRequestJpaRepository
                .findByRoomIdAndUserIdAndIdempotencyKey(roomId, userId, idempotencyKey)
                .map(joinRequestMapper::toDomain);
    }

    @Override
    public Optional<JoinRequest> findPendingByRoomIdAndUserId(UUID roomId, UUID userId) {
        return joinRequestJpaRepository
                .findByRoomIdAndUserIdAndState(roomId, userId, JoinRequestState.PENDING)
                .map(joinRequestMapper::toDomain);
    }

    @Override
    public List<JoinRequest> findPendingByRoomId(UUID roomId) {
        return joinRequestJpaRepository
                .findAllByRoomIdAndStateOrderByCreatedAtAsc(roomId, JoinRequestState.PENDING).stream()
                .map(joinRequestMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteAllByRoomId(UUID roomId) {
        joinRequestJpaRepository.deleteAllByRoomId(roomId);
    }

    @Override
    public int expireIdempotencyKeys(Instant expiredBefore) {
        return joinRequestJpaRepository.expireIdempotencyKeys(expiredBefore);
    }

    private JoinRequestJpaEntity toManaged(JoinRequest request) {
        if (request.isNew()) {
            return joinRequestMapper.toEntity(request);
        }
        JoinRequestJpaEntity target = joinRequestJpaRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalStateException("Join request no longer exists: " + request.getId()));
        joinRequestMapper.applyTo(request, target);
        return target;
    }
}