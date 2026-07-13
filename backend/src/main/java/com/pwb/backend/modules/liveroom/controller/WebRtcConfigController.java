package com.pwb.backend.modules.liveroom.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.liveroom.dto.response.IceServerListResponse;
import com.pwb.backend.modules.liveroom.service.IceServerProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webrtc")
@RequiredArgsConstructor
public class WebRtcConfigController {

    private static final String MSG_ICE_CONFIG_FETCHED = "WEBRTC_ICE_CONFIG_FETCHED";

    private final IceServerProvider iceServerProvider;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<IceServerListResponse>> getIceConfig() {
        UUID userId = currentUserResolver.resolveUserId();
        IceServerListResponse data = iceServerProvider.provide(userId);
        log.info("ICE_CONFIG_REQUESTED userId={} servers={}",
                userId, data.getIceServers() == null ? 0 : data.getIceServers().size());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_ICE_CONFIG_FETCHED), data));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
