package com.pwb.backend.modules.liveroom.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.liveroom.dto.request.ApproveRejectRequest;
import com.pwb.backend.modules.liveroom.dto.request.CreateRoomRequest;
import com.pwb.backend.modules.liveroom.dto.request.JoinRoomRequest;
import com.pwb.backend.modules.liveroom.dto.request.SelectSourceRequest;
import com.pwb.backend.modules.liveroom.dto.response.CreateRoomResponse;
import com.pwb.backend.modules.liveroom.dto.response.JoinRoomResponse;
import com.pwb.backend.modules.liveroom.dto.response.WaitingListResponse;
import com.pwb.backend.modules.liveroom.service.ListenerJoinService;
import com.pwb.backend.modules.liveroom.service.PlaybackService;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import com.pwb.backend.modules.liveroom.service.WaitingListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class LiveRoomController {

    private static final String MSG_ROOM_CREATED = "LIVE_ROOM_CREATED";
    private static final String MSG_JOIN_APPROVED = "JOIN_APPROVED";
    private static final String MSG_JOIN_WAITING = "JOIN_WAITING";
    private static final String MSG_WAITING_LIST_FETCHED = "WAITING_LIST_FETCHED";
    private static final String MSG_LISTENER_APPROVED = "LISTENER_APPROVED";
    private static final String MSG_LISTENER_REJECTED = "LISTENER_REJECTED";
    private static final String MSG_LISTENER_KICKED = "LISTENER_KICKED";
    private static final String MSG_SOURCE_SELECTED = "SOURCE_SELECTED";

    private final RoomLifecycleService roomLifecycleService;
    private final ListenerJoinService listenerJoinService;
    private final WaitingListService waitingListService;
    private final PlaybackService playbackService;
    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @PostMapping
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<CreateRoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {
        UUID hostId = currentUserResolver.resolveUserId();
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND,
                        "Host " + hostId + " not found"));
        String hostDisplayName = host.getFullName();
        if (hostDisplayName == null || hostDisplayName.isBlank()) {
            hostDisplayName = host.getEmail();
        }
        CreateRoomResponse data = roomLifecycleService.createRoom(request.mode(), hostId, hostDisplayName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message(MSG_ROOM_CREATED), data));
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(
            @PathVariable String roomCode,
            @Valid @RequestBody JoinRoomRequest request) {
        JoinRoomResponse data = listenerJoinService.joinRoom(roomCode, request.displayName());
        String msg = data.accessGranted() ? MSG_JOIN_APPROVED : MSG_JOIN_WAITING;
        return ResponseEntity.ok(ApiResponse.success(message(msg), data));
    }

    @GetMapping("/{roomCode}/waiting")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<WaitingListResponse>> listWaiting(@PathVariable String roomCode) {
        UUID hostId = currentUserResolver.resolveUserId();
        WaitingListResponse data = waitingListService.listWaiting(roomCode, hostId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_WAITING_LIST_FETCHED), data));
    }

    @PostMapping("/{roomCode}/waiting/approve")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable String roomCode,
            @Valid @RequestBody ApproveRejectRequest request) {
        UUID hostId = currentUserResolver.resolveUserId();
        waitingListService.approve(roomCode, hostId, request.listenerId());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LISTENER_APPROVED)));
    }

    @PostMapping("/{roomCode}/waiting/reject")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable String roomCode,
            @Valid @RequestBody ApproveRejectRequest request) {
        UUID hostId = currentUserResolver.resolveUserId();
        waitingListService.reject(roomCode, hostId, request.listenerId());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LISTENER_REJECTED)));
    }

    @PostMapping("/{roomCode}/waiting/kick")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<Void>> kick(
            @PathVariable String roomCode,
            @Valid @RequestBody ApproveRejectRequest request) {
        UUID hostId = currentUserResolver.resolveUserId();
        waitingListService.kick(roomCode, hostId, request.listenerId());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LISTENER_KICKED)));
    }

    @PostMapping("/{roomCode}/source")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<Void>> selectSource(
            @PathVariable String roomCode,
            @Valid @RequestBody SelectSourceRequest request) {
        UUID hostId = currentUserResolver.resolveUserId();
        playbackService.selectSource(roomCode, hostId, request.demoId());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_SOURCE_SELECTED)));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}