package com.pwb.liveroom.api.controller;

import com.pwb.liveroom.api.dto.request.CreateRoomRequest;
import com.pwb.liveroom.api.dto.response.RoomLookupResponse;
import com.pwb.liveroom.api.dto.response.RoomResponse;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.FindRoomByCodeCommand;
import com.pwb.liveroom.application.usecase.CreateRoomUseCase;
import com.pwb.liveroom.application.usecase.EndRoomUseCase;
import com.pwb.liveroom.application.usecase.FindRoomByCodeUseCase;
import com.pwb.liveroom.application.usecase.GetRoomUseCase;
import com.pwb.liveroom.application.usecase.ListRoomsUseCase;
import com.pwb.liveroom.application.usecase.ReopenRoomUseCase;
import com.pwb.liveroom.application.usecase.SearchRoomsUseCase;
import com.pwb.liveroom.application.usecase.UndoEndRoomUseCase;
import com.pwb.liveroom.application.view.RoomLookupView;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.enums.RoomStatus;
import com.pwb.liveroom.domain.repository.RoomSearchCriteria;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.dto.PageResponse;
import com.pwb.web.dto.PageResponses;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.AuthenticatedUser;
import com.pwb.web.security.CurrentClientIp;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/liveroom/rooms")
@RequiredArgsConstructor
@Validated
public class RoomController {

    private static final String MSG_ROOM_CREATED = "LIVEROOM_ROOM_CREATED";
    private static final String MSG_ROOM_RETRIEVED = "LIVEROOM_ROOM_RETRIEVED";
    private static final String MSG_ROOM_ENDED = "LIVEROOM_ROOM_ENDED";
    private static final String MSG_ROOM_REVIVED = "LIVEROOM_ROOM_REVIVED";
    private static final String MSG_ROOM_REOPENED = "LIVEROOM_ROOM_REOPENED";

    private final CreateRoomUseCase createRoom;
    private final GetRoomUseCase getRoom;
    private final ListRoomsUseCase listRooms;
    private final SearchRoomsUseCase searchRooms;
    private final FindRoomByCodeUseCase findRoomByCode;
    private final EndRoomUseCase endRoom;
    private final UndoEndRoomUseCase undoEndRoom;
    private final ReopenRoomUseCase reopenRoom;
    private final MessageResolver messageResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        RoomView view = createRoom.execute(request.toCommand(Actors.from(user)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_ROOM_CREATED), RoomResponse.from(view)));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RoomResponse>>> list(
            @CurrentUser UUID userId,
            @RequestParam(required = false) RoomStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<RoomView> page = listRooms.execute(userId, status, pageable);
        PageResponse<RoomResponse> body = PageResponses.from(page, RoomResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }


    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<RoomResponse>>> search(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RoomStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        RoomSearchCriteria criteria = new RoomSearchCriteria(userId, q, status);
        Page<RoomView> page = searchRooms.execute(criteria, pageable);
        PageResponse<RoomResponse> body = PageResponses.from(page, RoomResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomResponse>> getById(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        RoomView view = getRoom.execute(userId, roomId);
        return ResponseEntity.ok(
                ApiResponse.success(messageResolver.get(MSG_ROOM_RETRIEVED), RoomResponse.from(view)));
    }


    @GetMapping("/by-code/{roomCode}")
    public ResponseEntity<ApiResponse<RoomLookupResponse>> getByCode(
            @PathVariable String roomCode,
            @CurrentClientIp String clientIp
    ) {
        RoomLookupView view = findRoomByCode.execute(new FindRoomByCodeCommand(roomCode, clientIp));
        return ResponseEntity.ok(
                ApiResponse.success(messageResolver.get(MSG_ROOM_RETRIEVED), RoomLookupResponse.from(view)));
    }

    @PostMapping("/{roomId}/end")
    public ResponseEntity<ApiResponse<RoomResponse>> end(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        RoomView view = endRoom.execute(userId, roomId);
        return ResponseEntity.ok(
                ApiResponse.success(messageResolver.get(MSG_ROOM_ENDED), RoomResponse.from(view)));
    }


    @PostMapping("/{roomId}/undo-end")
    public ResponseEntity<ApiResponse<RoomResponse>> undoEnd(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        RoomView view = undoEndRoom.execute(userId, roomId);
        return ResponseEntity.ok(
                ApiResponse.success(messageResolver.get(MSG_ROOM_REVIVED), RoomResponse.from(view)));
    }

    @PostMapping("/{roomId}/reopen")
    public ResponseEntity<ApiResponse<RoomResponse>> reopen(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID roomId
    ) {
        RoomView view = reopenRoom.execute(Actors.from(user), roomId);
        return ResponseEntity.ok(
                ApiResponse.success(messageResolver.get(MSG_ROOM_REOPENED), RoomResponse.from(view)));
    }
}