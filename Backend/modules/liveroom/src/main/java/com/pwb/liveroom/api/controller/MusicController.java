package com.pwb.liveroom.api.controller;

import com.pwb.liveroom.api.dto.response.RoomAudioUrlResponse;
import com.pwb.liveroom.application.usecase.GetRoomAudioUrlUseCase;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/liveroom/rooms/{roomId}/music")
@RequiredArgsConstructor
@Validated
public class MusicController {

    private final GetRoomAudioUrlUseCase getRoomAudioUrl;

    @GetMapping("/audio-url")
    public ResponseEntity<ApiResponse<RoomAudioUrlResponse>> audioUrl(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        RoomAudioUrlResponse body = RoomAudioUrlResponse.from(getRoomAudioUrl.execute(userId, roomId));
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}