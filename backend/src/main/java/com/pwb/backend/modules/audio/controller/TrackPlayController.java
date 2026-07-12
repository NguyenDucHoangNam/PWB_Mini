package com.pwb.backend.modules.audio.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.modules.audio.service.PlayCountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demos/shared")
@RequiredArgsConstructor
public class TrackPlayController {

    private static final String MSG_PLAY_COUNT_RECORDED = "PLAY_COUNT_RECORDED";

    private final PlayCountService playCountService;
    private final MessageSource messageSource;

    @PostMapping("/{shareToken}/track-play")
    public ResponseEntity<ApiResponse<Void>> trackPlay(
            @PathVariable UUID shareToken,
            HttpServletRequest request) {
        playCountService.recordPlay(shareToken, request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_PLAY_COUNT_RECORDED), null));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
