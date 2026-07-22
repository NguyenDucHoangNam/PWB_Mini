package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.api.LiveRoomFacade;
import com.pwb.liveroom.api.dto.request.CreateLiveRoomRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomExistsResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomSummaryResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomViewerStatusResponse;
import com.pwb.liveroom.core.model.JoinRequestStatus;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJoinRequestJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJoinRequestJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomParticipantJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomFacadeImpl implements LiveRoomFacade {

    private final LiveRoomService liveRoomService;
    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomJoinRequestJpaRepository joinRequestJpaRepository;

    @Override
    @Transactional
    public LiveRoomResponse createRoom(UUID hostUserId, CreateLiveRoomRequest request) {
        log.info("Facade createRoom: hostUserId={}", hostUserId);

        LiveRoom domain = liveRoomService.createRoom(
                hostUserId,
                request.getTitle(),
                request.getDescription(),
                request.getMode(),
                request.getMaxParticipants()
        );

        return toResponse(domain);
    }

    @Override
    public Page<LiveRoomSummaryResponse> listMyRooms(UUID hostUserId, LiveRoomStatus status, Pageable pageable) {
        log.debug("Facade listMyRooms: hostUserId={}, status={}", hostUserId, status);
        return liveRoomService.listMyRooms(hostUserId, status, pageable)
                .map(this::toSummary);
    }

    @Override
    public LiveRoomResponse getRoom(UUID hostUserId, String roomCode) {
        LiveRoom domain = liveRoomService.getRoomByCode(hostUserId, roomCode);
        return toResponse(domain);
    }

    @Override
    @Transactional
    public void endRoom(UUID hostUserId, String roomCode) {
        log.info("Facade endRoom: hostUserId={}, roomCode={}", hostUserId, roomCode);
        liveRoomService.endRoom(hostUserId, roomCode);
    }

    @Override
    public LiveRoomExistsResponse checkRoomExists(String roomCode) {
        return liveRoomJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .map(this::toExistsResponse)
                .orElseGet(() -> LiveRoomExistsResponse.builder()
                        .roomCode(roomCode)
                        .exists(false)
                        .active(false)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public LiveRoomViewerStatusResponse getViewerStatus(UUID viewerUserId, String roomCode) {
        log.debug("Facade getViewerStatus: viewerUserId={}, roomCode={}", viewerUserId, roomCode);

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        boolean isHost = roomEntity.getHostUserId().equals(viewerUserId);
        boolean isParticipant = !isHost && participantJpaRepository
                .findActiveByRoomAndUser(roomCode, viewerUserId)
                .isPresent();

        Optional<LiveRoomJoinRequestJpaEntity> pendingRequest =
                joinRequestJpaRepository.findActiveByRoomAndUser(
                        roomCode, viewerUserId, JoinRequestStatus.PENDING);

        return LiveRoomViewerStatusResponse.builder()
                .viewerUserId(viewerUserId)
                .host(isHost)
                .participant(isParticipant)
                .pendingRequest(pendingRequest.isPresent())
                .pendingRequestId(pendingRequest.map(LiveRoomJoinRequestJpaEntity::getId).orElse(null))
                .pendingStatus(pendingRequest.map(LiveRoomJoinRequestJpaEntity::getStatus).orElse(null))
                .roomStatus(roomEntity.getStatus())
                .roomMode(roomEntity.getMode())
                .roomCode(roomEntity.getRoomCode())
                .hostUserId(roomEntity.getHostUserId())
                .createdAt(roomEntity.getCreatedAt())
                .build();
    }

    private LiveRoomResponse toResponse(LiveRoom domain) {
        if (domain == null) {
            return null;
        }
        int availableSlots = Math.max(0, domain.getMaxParticipants() - domain.getCurrentParticipantCount());
        return LiveRoomResponse.builder()
                .id(domain.getId())
                .hostUserId(domain.getHostUserId())
                .roomCode(domain.getRoomCode())
                .title(domain.getTitle())
                .description(domain.getDescription())
                .mode(domain.getMode())
                .maxParticipants(domain.getMaxParticipants())
                .currentParticipantCount(domain.getCurrentParticipantCount())
                .availableSlots(availableSlots)
                .status(domain.getStatus())
                .scheduledStartAt(domain.getScheduledStartAt())
                .startedAt(domain.getStartedAt())
                .endedAt(domain.getEndedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    private LiveRoomSummaryResponse toSummary(LiveRoom domain) {
        if (domain == null) {
            return null;
        }
        return LiveRoomSummaryResponse.builder()
                .id(domain.getId())
                .roomCode(domain.getRoomCode())
                .title(domain.getTitle())
                .mode(domain.getMode())
                .status(domain.getStatus())
                .maxParticipants(domain.getMaxParticipants())
                .currentParticipantCount(domain.getCurrentParticipantCount())
                .createdAt(domain.getCreatedAt())
                .endedAt(domain.getEndedAt())
                .build();
    }

    private LiveRoomExistsResponse toExistsResponse(LiveRoomJpaEntity entity) {
        return LiveRoomExistsResponse.builder()
                .roomCode(entity.getRoomCode())
                .exists(true)
                .active(entity.getStatus() == LiveRoomStatus.ACTIVE)
                .build();
    }
}
