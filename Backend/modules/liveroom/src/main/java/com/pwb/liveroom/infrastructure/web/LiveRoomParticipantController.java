package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import com.pwb.backend.security.CurrentUser;
import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
import com.pwb.liveroom.api.LiveRoomParticipantFacade;
import com.pwb.liveroom.api.dto.request.JoinLiveRoomRequest;
import com.pwb.liveroom.api.dto.response.LiveRoomJoinResponse;
import com.pwb.liveroom.api.dto.response.ParticipantSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live-rooms/{roomCode}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','PRO','ADMIN')")
public class LiveRoomParticipantController {

    private static final String MSG_JOINED = "LIVEROOM_JOINED";
    private static final String MSG_LEFT = "LIVEROOM_LEFT";
    private static final String MSG_PARTICIPANTS_RETRIEVED = "LIVEROOM_PARTICIPANTS_RETRIEVED";
    private static final String PATH_ROOM_CODE = "roomCode";

    private final LiveRoomParticipantFacade participantFacade;
    private final MessageResolver messageResolver;

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<LiveRoomJoinResponse>> join(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @Valid @RequestBody(required = false) JoinLiveRoomRequest request) {

        JoinLiveRoomRequest effective = request == null ? new JoinLiveRoomRequest() : request;
        String rawRole = user.getRole();
        String effectiveRole = rawRole != null && rawRole.startsWith("ROLE_") ? rawRole.substring(5) : rawRole;
        LiveRoomJoinResponse data = participantFacade.joinRoom(
                user.getId(),
                null,
                effectiveRole,
                roomCode,
                effective);
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_JOINED)));
    }

    @PostMapping("/leave")
    public ApiResponse<Void> leave(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {

        participantFacade.leaveRoom(user.getId(), roomCode);
        return ApiResponse.success(null, messageResolver.get(MSG_LEFT));
    }

    @GetMapping("/participants")
    public ApiResponse<List<ParticipantSummaryResponse>> listParticipants(
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {

        List<ParticipantSummaryResponse> data = participantFacade.listParticipants(roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_PARTICIPANTS_RETRIEVED));
    }
}