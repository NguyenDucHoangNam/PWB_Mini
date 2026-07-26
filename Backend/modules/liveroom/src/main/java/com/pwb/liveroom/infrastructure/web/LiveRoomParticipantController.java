package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import com.pwb.backend.security.CurrentUser;
import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
import com.pwb.liveroom.api.LiveRoomParticipantFacade;
import com.pwb.liveroom.api.dto.request.JoinLiveRoomRequest;
import com.pwb.liveroom.api.dto.request.MediaStateUpdateRequest;
import com.pwb.liveroom.api.dto.response.ParticipantSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private static final String MSG_LEFT = "LIVEROOM_LEFT";
    private static final String MSG_JOINED = "LIVEROOM_JOINED";
    private static final String MSG_PARTICIPANTS_RETRIEVED = "LIVEROOM_PARTICIPANTS_RETRIEVED";
    private static final String MSG_MEDIA_UPDATED = "LIVEROOM_PARTICIPANT_MEDIA_UPDATED";
    private static final String PATH_ROOM_CODE = "roomCode";

    private final LiveRoomParticipantFacade participantFacade;
    private final MessageResolver messageResolver;

    @PostMapping("/join")
    public ApiResponse<ParticipantSummaryResponse> joinPublicRoom(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @Valid @RequestBody JoinLiveRoomRequest body) {

        String displayName = body.getDisplayName() != null && !body.getDisplayName().isBlank()
                ? body.getDisplayName()
                : user.getUsername();
        ParticipantSummaryResponse data = participantFacade.joinPublicRoom(
                user.getId(),
                roomCode,
                displayName,
                user.getRole(),
                body.getMicMuted(),
                body.getCameraOff());
        return ApiResponse.success(data, messageResolver.get(MSG_JOINED));
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

    @PatchMapping("/participants/me/media")
    public ApiResponse<ParticipantSummaryResponse> updateMyMedia(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @Valid @RequestBody MediaStateUpdateRequest request) {

        ParticipantSummaryResponse data = participantFacade.updateMediaState(
                user.getId(), roomCode, request.getMicMuted(), request.getCameraOff());
        return ApiResponse.success(data, messageResolver.get(MSG_MEDIA_UPDATED));
    }
}
