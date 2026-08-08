package com.pwb.liveroom.api.controller;

import com.pwb.liveroom.api.dto.request.ModerateParticipantRequest;
import com.pwb.liveroom.api.dto.request.UpdateMediaStateRequest;
import com.pwb.liveroom.api.dto.response.ParticipantResponse;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.ModerateParticipantCommand;
import com.pwb.liveroom.application.command.UpdateMediaStateCommand;
import com.pwb.liveroom.application.usecase.JoinRoomUseCase;
import com.pwb.liveroom.application.usecase.KickParticipantUseCase;
import com.pwb.liveroom.application.usecase.LeaveRoomUseCase;
import com.pwb.liveroom.application.usecase.ListParticipantsUseCase;
import com.pwb.liveroom.application.usecase.RemoteMuteParticipantUseCase;
import com.pwb.liveroom.application.usecase.UpdateMediaStateUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.AuthenticatedUser;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/liveroom/rooms/{roomId}/participants")
@RequiredArgsConstructor
@Validated
public class ParticipantController {

    private static final String MSG_ROOM_JOINED = "LIVEROOM_ROOM_JOINED";
    private static final String MSG_ROOM_LEFT = "LIVEROOM_ROOM_LEFT";
    private static final String MSG_MEDIA_UPDATED = "LIVEROOM_MEDIA_UPDATED";
    private static final String MSG_PARTICIPANT_KICKED = "LIVEROOM_PARTICIPANT_KICKED";
    private static final String MSG_MIC_MUTED = "LIVEROOM_MIC_MUTED";

    private final JoinRoomUseCase joinRoom;
    private final LeaveRoomUseCase leaveRoom;
    private final ListParticipantsUseCase listParticipants;
    private final UpdateMediaStateUseCase updateMediaState;
    private final KickParticipantUseCase kickParticipant;
    private final RemoteMuteParticipantUseCase remoteMuteParticipant;
    private final MessageResolver messageResolver;


    @PostMapping("/me")
    public ResponseEntity<ApiResponse<ParticipantResponse>> join(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID roomId
    ) {
        ParticipantView view = joinRoom.execute(Actors.from(user), roomId);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_ROOM_JOINED),
                ParticipantResponse.from(view)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<ParticipantResponse>> leave(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        ParticipantView view = leaveRoom.execute(userId, roomId);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_ROOM_LEFT),
                ParticipantResponse.from(view)));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<ParticipantResponse>>> list(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        List<ParticipantResponse> body = listParticipants.execute(userId, roomId).stream()
                .map(ParticipantResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }


    @PatchMapping("/me/media")
    public ResponseEntity<ApiResponse<ParticipantResponse>> updateMedia(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateMediaStateRequest request
    ) {
        ParticipantView view = updateMediaState.execute(
                new UpdateMediaStateCommand(userId, roomId, request.cameraOn(), request.micOn()));
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_MEDIA_UPDATED),
                ParticipantResponse.from(view)));
    }


    @PostMapping("/{targetUserId}/kick")
    public ResponseEntity<ApiResponse<ParticipantResponse>> kick(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody(required = false) ModerateParticipantRequest request
    ) {
        ParticipantView view = kickParticipant.execute(new ModerateParticipantCommand(
                userId, roomId, targetUserId, request == null ? null : request.reason()));
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PARTICIPANT_KICKED),
                ParticipantResponse.from(view)));
    }


    @PostMapping("/{targetUserId}/mute")
    public ResponseEntity<ApiResponse<ParticipantResponse>> mute(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody(required = false) ModerateParticipantRequest request
    ) {
        ParticipantView view = remoteMuteParticipant.execute(new ModerateParticipantCommand(
                userId, roomId, targetUserId, request == null ? null : request.reason()));
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_MIC_MUTED),
                ParticipantResponse.from(view)));
    }
}