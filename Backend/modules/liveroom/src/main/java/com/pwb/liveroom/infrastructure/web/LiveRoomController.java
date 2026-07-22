package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import com.pwb.backend.security.CurrentUser;
import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
import com.pwb.liveroom.api.LiveRoomFacade;
import com.pwb.liveroom.api.dto.request.CreateLiveRoomRequest;
import com.pwb.liveroom.api.dto.request.UpdateLiveRoomSettingsRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomExistsResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomSummaryResponse;
import com.pwb.liveroom.api.dto.response.LiveRoomViewerStatusResponse;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/live-rooms")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'PRO', 'ADMIN')")
public class LiveRoomController {

    private static final String MSG_CREATED = "LIVEROOM_CREATED";
    private static final String MSG_UPDATED = "LIVEROOM_UPDATED";
    private static final String MSG_ENDED = "LIVEROOM_ENDED";
    private static final String MSG_RETRIEVED = "LIVEROOM_RETRIEVED";
    private static final String MSG_LIST_RETRIEVED = "LIVEROOM_LIST_RETRIEVED";
    private static final String MSG_EXISTS_CHECKED = "LIVEROOM_EXISTS_CHECKED";
    private static final String MSG_VIEWER_STATUS_RETRIEVED = "LIVEROOM_VIEWER_STATUS_RETRIEVED";
    private static final String PATH_ROOM_CODE = "roomCode";

    private final LiveRoomFacade liveRoomFacade;
    private final MessageResolver messageResolver;

    @PostMapping
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<LiveRoomResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody CreateLiveRoomRequest request) {
        LiveRoomResponse data = liveRoomFacade.createRoom(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_CREATED)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PRO')")
    public ApiResponse<Page<LiveRoomSummaryResponse>> listMine(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(name = "status", required = false) LiveRoomStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<LiveRoomSummaryResponse> data = liveRoomFacade.listMyRooms(user.getId(), status, pageable);
        return ApiResponse.success(data, messageResolver.get(MSG_LIST_RETRIEVED));
    }

    @GetMapping("/{" + PATH_ROOM_CODE + "}")
    @PreAuthorize("hasAnyRole('USER', 'PRO', 'ADMIN')")
    public ApiResponse<LiveRoomResponse> get(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        LiveRoomResponse data = liveRoomFacade.getRoom(user.getId(), roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_RETRIEVED));
    }

    @PatchMapping("/{" + PATH_ROOM_CODE + "}")
    @PreAuthorize("hasRole('PRO')")
    public ApiResponse<LiveRoomResponse> update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @Valid @RequestBody UpdateLiveRoomSettingsRequest request) {
        LiveRoomResponse data = liveRoomFacade.updateRoom(user.getId(), roomCode, request);
        return ApiResponse.success(data, messageResolver.get(MSG_UPDATED));
    }

    @PostMapping("/{" + PATH_ROOM_CODE + "}/end")
    @PreAuthorize("hasRole('PRO')")
    public ApiResponse<Void> end(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        liveRoomFacade.endRoom(user.getId(), roomCode);
        return ApiResponse.success(null, messageResolver.get(MSG_ENDED));
    }

    @GetMapping("/{" + PATH_ROOM_CODE + "}/exists")
    @PreAuthorize("hasAnyRole('USER', 'PRO', 'ADMIN')")
    public ApiResponse<LiveRoomExistsResponse> exists(
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        LiveRoomExistsResponse data = liveRoomFacade.checkRoomExists(roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_EXISTS_CHECKED));
    }

    @GetMapping("/{" + PATH_ROOM_CODE + "}/me/status")
    @PreAuthorize("hasAnyRole('USER', 'PRO', 'ADMIN')")
    public ApiResponse<LiveRoomViewerStatusResponse> myStatus(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        LiveRoomViewerStatusResponse data = liveRoomFacade.getViewerStatus(user.getId(), roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_VIEWER_STATUS_RETRIEVED));
    }
}