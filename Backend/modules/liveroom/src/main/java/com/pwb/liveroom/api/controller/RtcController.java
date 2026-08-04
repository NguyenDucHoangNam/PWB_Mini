package com.pwb.liveroom.api.controller;

import com.pwb.liveroom.api.dto.response.RtcConfigResponse;
import com.pwb.liveroom.application.usecase.GetRtcConfigUseCase;
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
@RequestMapping("/api/v1/liveroom/rooms/{roomId}/rtc")
@RequiredArgsConstructor
@Validated
public class RtcController {

    private final GetRtcConfigUseCase getRtcConfig;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<RtcConfigResponse>> config(
            @CurrentUser UUID userId,
            @PathVariable UUID roomId
    ) {
        RtcConfigResponse body = RtcConfigResponse.from(getRtcConfig.execute(userId, roomId));
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
