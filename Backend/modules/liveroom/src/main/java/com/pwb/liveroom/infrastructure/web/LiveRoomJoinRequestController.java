package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import com.pwb.backend.security.CurrentUser;
import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
import com.pwb.liveroom.api.LiveRoomJoinRequestFacade;
import com.pwb.liveroom.api.dto.request.CreateJoinRequestRequest;
import com.pwb.liveroom.api.dto.request.JoinRequestDecisionRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomJoinRequestResponse;
import com.pwb.liveroom.core.model.JoinRequestStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/live-rooms/{roomCode}/join-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','PRO','ADMIN')")
public class LiveRoomJoinRequestController {

    private static final String MSG_CREATED = "LIVEROOM_JOIN_REQUEST_CREATED";
    private static final String MSG_LIST_RETRIEVED = "LIVEROOM_JOIN_REQUEST_LIST_RETRIEVED";
    private static final String MSG_APPROVED = "LIVEROOM_JOIN_REQUEST_APPROVED";
    private static final String MSG_REJECTED = "LIVEROOM_JOIN_REQUEST_REJECTED";
    private static final String MSG_CANCELLED = "LIVEROOM_JOIN_REQUEST_CANCELLED";
    private static final String PATH_ROOM_CODE = "roomCode";
    private static final String PATH_REQUEST_ID = "requestId";

    private final LiveRoomJoinRequestFacade joinRequestFacade;
    private final MessageResolver messageResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<LiveRoomJoinRequestResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @Valid @RequestBody(required = false) CreateJoinRequestRequest request) {

        CreateJoinRequestRequest effective = request == null ? new CreateJoinRequestRequest() : request;
        LiveRoomJoinRequestResponse data = joinRequestFacade.createOrReturnPending(
                user.getId(), roomCode, effective);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_CREATED)));
    }

    @GetMapping
    @PreAuthorize("hasRole('PRO')")
    public ApiResponse<List<LiveRoomJoinRequestResponse>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @RequestParam(name = "status", required = false) JoinRequestStatus status) {

        List<LiveRoomJoinRequestResponse> data =
                joinRequestFacade.listByRoom(user.getId(), roomCode, status);
        return ApiResponse.success(data, messageResolver.get(MSG_LIST_RETRIEVED));
    }

    @PostMapping("/{" + PATH_REQUEST_ID + "}/approve")
    @PreAuthorize("hasRole('PRO')")
    public ApiResponse<LiveRoomJoinRequestResponse> approve(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @PathVariable(name = PATH_REQUEST_ID) UUID requestId,
            @Valid @RequestBody(required = false) JoinRequestDecisionRequest request) {

        LiveRoomJoinRequestResponse data = joinRequestFacade.approve(user.getId(), requestId, request);
        return ApiResponse.success(data, messageResolver.get(MSG_APPROVED));
    }

    @PostMapping("/{" + PATH_REQUEST_ID + "}/reject")
    @PreAuthorize("hasRole('PRO')")
    public ApiResponse<LiveRoomJoinRequestResponse> reject(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @PathVariable(name = PATH_REQUEST_ID) UUID requestId,
            @Valid @RequestBody(required = false) JoinRequestDecisionRequest request) {

        LiveRoomJoinRequestResponse data = joinRequestFacade.reject(user.getId(), requestId, request);
        return ApiResponse.success(data, messageResolver.get(MSG_REJECTED));
    }

    @DeleteMapping("/{" + PATH_REQUEST_ID + "}")
    public ApiResponse<Void> cancel(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @PathVariable(name = PATH_REQUEST_ID) UUID requestId) {

        joinRequestFacade.cancel(user.getId(), requestId);
        return ApiResponse.success(null, messageResolver.get(MSG_CANCELLED));
    }
}
