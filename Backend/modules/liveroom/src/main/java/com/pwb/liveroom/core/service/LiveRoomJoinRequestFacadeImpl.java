package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.LiveRoomJoinRequestFacade;
import com.pwb.liveroom.api.dto.request.CreateJoinRequestRequest;
import com.pwb.liveroom.api.dto.request.JoinRequestDecisionRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomJoinRequestResponse;
import com.pwb.liveroom.core.model.JoinRequestStatus;
import com.pwb.liveroom.core.model.LiveRoomJoinRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomJoinRequestFacadeImpl implements LiveRoomJoinRequestFacade {

    private final LiveRoomJoinRequestService joinRequestService;

    @Override
    public LiveRoomJoinRequestResponse createOrReturnPending(
            UUID userId, String roomCode, CreateJoinRequestRequest request) {

        log.info("Facade createOrReturnPending: userId={}, roomCode={}", userId, roomCode);

        String displayName = request == null || request.getDisplayName() == null
                ? "guest-" + userId.toString().substring(0, 8)
                : request.getDisplayName();
        String message = request == null ? null : request.getMessage();

        LiveRoomJoinRequest domain = joinRequestService.createOrReturnPending(
                userId, roomCode, displayName, message);
        return toResponse(domain);
    }

    @Override
    public List<LiveRoomJoinRequestResponse> listByRoom(UUID hostUserId, String roomCode, JoinRequestStatus status) {
        log.debug("Facade listByRoom: roomCode={}, status={}", roomCode, status);
        return joinRequestService.listByRoom(roomCode, hostUserId, status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public LiveRoomJoinRequestResponse approve(UUID hostUserId, UUID requestId, JoinRequestDecisionRequest request) {
        String reason = request == null ? null : request.getReason();
        LiveRoomJoinRequest domain = joinRequestService.approve(hostUserId, requestId, reason);
        return toResponse(domain);
    }

    @Override
    public LiveRoomJoinRequestResponse reject(UUID hostUserId, UUID requestId, JoinRequestDecisionRequest request) {
        String reason = request == null ? null : request.getReason();
        LiveRoomJoinRequest domain = joinRequestService.reject(hostUserId, requestId, reason);
        return toResponse(domain);
    }

    @Override
    public void cancel(UUID ownerUserId, UUID requestId) {
        joinRequestService.cancel(ownerUserId, requestId);
    }

    private LiveRoomJoinRequestResponse toResponse(LiveRoomJoinRequest domain) {
        if (domain == null) {
            return null;
        }
        return LiveRoomJoinRequestResponse.builder()
                .id(domain.getId())
                .roomCode(domain.getRoomCode())
                .userId(domain.getUserId())
                .displayName(domain.getDisplayName())
                .message(domain.getMessage())
                .status(domain.getStatus())
                .decisionReason(domain.getDecisionReason())
                .decidedByUserId(domain.getDecidedByUserId())
                .decidedAt(domain.getDecidedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
