package com.pwb.liveroom.api.controller;

import com.pwb.liveroom.api.dto.request.CreateJoinRequestRequest;
import com.pwb.liveroom.api.dto.response.JoinRequestResponse;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.CreateJoinRequestCommand;
import com.pwb.liveroom.application.usecase.ApproveJoinRequestUseCase;
import com.pwb.liveroom.application.usecase.CancelJoinRequestUseCase;
import com.pwb.liveroom.application.usecase.CreateJoinRequestUseCase;
import com.pwb.liveroom.application.usecase.ListPendingJoinRequestsUseCase;
import com.pwb.liveroom.application.usecase.RejectJoinRequestUseCase;
import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.AuthenticatedUser;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/liveroom/rooms/{roomId}/join-requests")
@RequiredArgsConstructor
@Validated
public class JoinRequestController {

    private static final String MSG_REQUEST_CREATED = "LIVEROOM_REQUEST_CREATED";
    private static final String MSG_REQUEST_CANCELLED = "LIVEROOM_REQUEST_CANCELLED";
    private static final String MSG_REQUEST_APPROVED = "LIVEROOM_REQUEST_APPROVED";
    private static final String MSG_REQUEST_REJECTED = "LIVEROOM_REQUEST_REJECTED";
    private static final String MSG_ROOM_FULL = "LIVEROOM_ROOM_FULL";

    private final CreateJoinRequestUseCase createJoinRequest;
    private final CancelJoinRequestUseCase cancelJoinRequest;
    private final ListPendingJoinRequestsUseCase listPendingJoinRequests;
    private final ApproveJoinRequestUseCase approveJoinRequest;
    private final RejectJoinRequestUseCase rejectJoinRequest;
    private final MessageResolver messageResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<JoinRequestResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateJoinRequestRequest request
    ) {
        JoinRequestView view = createJoinRequest.execute(
                new CreateJoinRequestCommand(Actors.from(user), roomId, request.idempotencyKey()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_REQUEST_CREATED),
                        JoinRequestResponse.from(view)));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<JoinRequestResponse>>> listPending(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        List<JoinRequestResponse> body = listPendingJoinRequests.execute(userId, roomId).stream()
                .map(JoinRequestResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @DeleteMapping("/{requestId}")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> cancel(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @PathVariable UUID requestId
    ) {
        JoinRequestView view = cancelJoinRequest.execute(userId, roomId, requestId);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_REQUEST_CANCELLED),
                JoinRequestResponse.from(view)));
    }


    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> approve(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @PathVariable UUID requestId
    ) {
        JoinRequestView view = approveJoinRequest.execute(userId, roomId, requestId);
        String message = view.state() == JoinRequestState.REJECTED_BY_CAPACITY
                ? messageResolver.get(MSG_ROOM_FULL)
                : messageResolver.get(MSG_REQUEST_APPROVED);
        return ResponseEntity.ok(ApiResponse.success(message, JoinRequestResponse.from(view)));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> reject(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @PathVariable UUID requestId
    ) {
        JoinRequestView view = rejectJoinRequest.execute(userId, roomId, requestId);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_REQUEST_REJECTED),
                JoinRequestResponse.from(view)));
    }
}