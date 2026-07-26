package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.core.model.JoinRequestStatus;
import com.pwb.liveroom.core.model.LiveroomDomainException;
import com.pwb.liveroom.core.model.LiveroomErrorCodeMapper;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomJoinRequest;
import com.pwb.liveroom.core.model.LiveRoomParticipant;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJoinRequestJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomParticipantJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomJoinRequestMapper;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomParticipantMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJoinRequestJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomParticipantJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomJoinRequestServiceImpl implements LiveRoomJoinRequestService {

    private final LiveRoomJoinRequestJpaRepository joinRequestJpaRepository;
    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomJoinRequestMapper joinRequestMapper;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomParticipantMapper participantMapper;
    private final LiveRoomRealtimeBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LiveRoomJoinRequest createOrReturnPending(
            UUID userId, String roomCode, String displayName, String message) {

        log.info("Creating join request: userId={}, roomCode={}", userId, roomCode);

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (roomEntity.getStatus() != LiveRoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        if (roomEntity.getMode() == com.pwb.liveroom.api.enums.LiveRoomMode.PUBLIC) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_REQUIRE_APPROVAL);
        }

        if (roomEntity.getHostUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_JOIN_REQUEST_INVALID_DECISION);
        }

        Optional<LiveRoomJoinRequestJpaEntity> existing =
                joinRequestJpaRepository.findActiveByRoomAndUser(roomCode, userId, JoinRequestStatus.PENDING);
        if (existing.isPresent()) {
            log.debug("Returning existing pending join request: userId={}, roomCode={}", userId, roomCode);
            return joinRequestMapper.toDomain(existing.get());
        }

        LiveRoomJoinRequest domain;
        try {
            domain = LiveRoomJoinRequest.create(roomCode, userId, displayName, message);
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        LiveRoomJoinRequestJpaEntity saved = joinRequestJpaRepository.save(joinRequestMapper.toEntity(domain));

        eventPublisher.publishEvent(new JoinRequestCreatedEvent(
                saved.getRoomCode(),
                roomEntity.getHostUserId(),
                saved.getId(),
                saved.getUserId(),
                saved.getDisplayName(),
                saved.getMessage(),
                saved.getCreatedAt()
        ));

        log.info("Join request created: requestId={}, userId={}, roomCode={}",
                saved.getId(), userId, roomCode);

        return joinRequestMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveRoomJoinRequest> listByRoom(String roomCode, UUID hostUserId, JoinRequestStatus status) {
        log.debug("Listing join requests: roomCode={}, status={}", roomCode, status);

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (!roomEntity.getHostUserId().equals(hostUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_HOST);
        }

        JoinRequestStatus effectiveStatus = status == null ? JoinRequestStatus.PENDING : status;
        return joinRequestJpaRepository.findByRoomCodeAndStatus(roomCode, effectiveStatus).stream()
                .map(joinRequestMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public LiveRoomJoinRequest approve(UUID hostUserId, UUID requestId, String reason) {
        log.info("Approving join request: requestId={}, hostUserId={}", requestId, hostUserId);

        LiveRoomJoinRequestJpaEntity entity = loadRequestAsHost(hostUserId, requestId);

        LiveRoomJoinRequest domain = joinRequestMapper.toDomain(entity);

        try {
            domain.approve(hostUserId, reason);
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        joinRequestJpaRepository.save(joinRequestMapper.toEntity(domain, entity));

        promoteToParticipant(domain);

        eventPublisher.publishEvent(new JoinRequestDecidedEvent(
                domain.getRoomCode(),
                hostUserId,
                domain.getId(),
                domain.getUserId(),
                JoinRequestStatus.APPROVED,
                domain.getDecisionReason(),
                domain.getDecidedAt()
        ));

        log.info("Join request approved: requestId={}, hostUserId={}", requestId, hostUserId);
        return domain;
    }

    @Override
    @Transactional
    public LiveRoomJoinRequest reject(UUID hostUserId, UUID requestId, String reason) {
        log.info("Rejecting join request: requestId={}, hostUserId={}", requestId, hostUserId);

        LiveRoomJoinRequestJpaEntity entity = loadRequestAsHost(hostUserId, requestId);

        LiveRoomJoinRequest domain = joinRequestMapper.toDomain(entity);

        try {
            domain.reject(hostUserId, reason);
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        joinRequestJpaRepository.save(joinRequestMapper.toEntity(domain, entity));

        eventPublisher.publishEvent(new JoinRequestDecidedEvent(
                domain.getRoomCode(),
                hostUserId,
                domain.getId(),
                domain.getUserId(),
                JoinRequestStatus.REJECTED,
                domain.getDecisionReason(),
                domain.getDecidedAt()
        ));

        log.info("Join request rejected: requestId={}, hostUserId={}", requestId, hostUserId);
        return domain;
    }

    @Override
    @Transactional
    public void cancel(UUID ownerUserId, UUID requestId) {
        log.info("Cancelling join request: requestId={}, ownerUserId={}", requestId, ownerUserId);

        LiveRoomJoinRequestJpaEntity entity = joinRequestJpaRepository
                .findActiveById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_FOUND));

        if (!entity.getUserId().equals(ownerUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_OWNER);
        }

        LiveRoomJoinRequest domain = joinRequestMapper.toDomain(entity);

        try {
            domain.cancel(ownerUserId);
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        joinRequestJpaRepository.save(joinRequestMapper.toEntity(domain, entity));

        eventPublisher.publishEvent(new JoinRequestDecidedEvent(
                domain.getRoomCode(),
                ownerUserId,
                domain.getId(),
                domain.getUserId(),
                JoinRequestStatus.CANCELLED,
                domain.getDecisionReason(),
                domain.getDecidedAt()
        ));

        log.info("Join request cancelled: requestId={}, ownerUserId={}", requestId, ownerUserId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestCreated(JoinRequestCreatedEvent event) {
        broadcaster.broadcastJoinRequestCreated(
                event.roomCode(),
                event.requestId(),
                event.requesterUserId(),
                event.displayName(),
                event.message(),
                event.createdAt().toString());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestDecided(JoinRequestDecidedEvent event) {
        broadcaster.pushJoinRequestDecided(
                event.requesterUserId(),
                event.roomCode(),
                event.requestId(),
                event.status().name(),
                event.reason(),
                event.decidedAt().toString());
    }

    private LiveRoomJoinRequestJpaEntity loadRequestAsHost(UUID hostUserId, UUID requestId) {
        LiveRoomJoinRequestJpaEntity entity = joinRequestJpaRepository
                .findActiveById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_FOUND));

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(entity.getRoomCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (!roomEntity.getHostUserId().equals(hostUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_HOST);
        }
        return entity;
    }

    private void promoteToParticipant(LiveRoomJoinRequest domain) {
        String roomCode = domain.getRoomCode();
        UUID userId = domain.getUserId();
        String displayName = domain.getDisplayName();
        String role = "USER";

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (existing.isPresent()) {
            log.debug("Participant already active (idempotent promote): userId={}, roomCode={}", userId, roomCode);
            return;
        }

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (roomEntity.getCurrentParticipantCount() >= roomEntity.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.LIVEROOM_FULL);
        }

        LiveRoomParticipant participant;
        try {
            participant = LiveRoomParticipant.join(roomCode, userId, displayName, role, Instant.now());
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        LiveRoomParticipantJpaEntity participantEntity =
                participantJpaRepository.save(participantMapper.toEntity(participant));

        LiveRoom liveRoomDomain = liveRoomMapper.toDomain(roomEntity);
        try {
            liveRoomDomain.incrementParticipants();
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }
        liveRoomMapper.toEntity(liveRoomDomain, roomEntity);
        liveRoomJpaRepository.save(roomEntity);

        eventPublisher.publishEvent(new LiveRoomParticipantServiceImpl.ParticipantJoinedEvent(
                participantEntity.getRoomCode(),
                roomEntity.getHostUserId(),
                participantEntity.getUserId(),
                participantEntity.getDisplayName(),
                participantEntity.getRoleAtJoin(),
                roomEntity.getCurrentParticipantCount(),
                roomEntity.getMaxParticipants(),
                Math.max(0, roomEntity.getMaxParticipants() - roomEntity.getCurrentParticipantCount()),
                participantEntity.getJoinedAt()
        ));

        log.info("Participant promoted from approved join request: userId={}, roomCode={}, requestId={}",
                userId, roomCode, domain.getId());
    }

    private BusinessException mapDomainException(LiveroomDomainException ex) {
        return LiveroomErrorCodeMapper.mapDomainException(ex);
    }

    public record JoinRequestCreatedEvent(
            String roomCode,
            UUID hostUserId,
            UUID requestId,
            UUID requesterUserId,
            String displayName,
            String message,
            Instant createdAt
    ) {}

    public record JoinRequestDecidedEvent(
            String roomCode,
            UUID hostUserId,
            UUID requestId,
            UUID requesterUserId,
            JoinRequestStatus status,
            String reason,
            Instant decidedAt
    ) {}
}
