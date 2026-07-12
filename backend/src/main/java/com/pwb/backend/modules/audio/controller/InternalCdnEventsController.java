package com.pwb.backend.modules.audio.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.modules.audio.dto.request.CdnEventRequest;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/cdn-events")
@RequiredArgsConstructor
public class InternalCdnEventsController {

    private static final String MSG_CDN_EVENT_RECORDED = "CDN_EVENT_RECORDED";

    private final StringRedisTemplate stringRedisTemplate;
    private final MessageSource messageSource;
    private final HttpClientContextResolver clientContextResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> recordEvent(@RequestBody CdnEventRequest payload,
                                                         HttpServletRequest request) {
        if (payload == null || payload.event() == null) {
            log.warn("CDN_EVENT_INVALID_PAYLOAD remoteIp={}", clientContextResolver.resolveIp(request));
            return ResponseEntity.ok(ApiResponse.success(message(MSG_CDN_EVENT_RECORDED), null));
        }
        log.warn("CDN_EVENT event={} shareToken={} cdnNodeIp={} reason={} timestamp={}",
                payload.event(), payload.shareToken(), payload.cdnNodeIp(),
                payload.rejectionReason(), payload.timestamp());
        if (isRejectionEvent(payload.event()) && payload.shareToken() != null) {
            try {
                String key = ShareRedisKeys.distributionRevokedKey(payload.shareToken());
                stringRedisTemplate.opsForValue().set(key, "cdn:" + payload.event(),
                        Duration.ofMinutes(15));
            } catch (Exception ex) {
                log.warn("CDN_EVENT_REDIS_RECORD_FAILED reason={}", ex.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.success(message(MSG_CDN_EVENT_RECORDED), null));
    }

    private static boolean isRejectionEvent(String event) {
        return "PLAYLIST_SIG_REJECTED".equalsIgnoreCase(event);
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}