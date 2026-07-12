package com.pwb.backend.modules.share.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.modules.audio.config.StreamProperties;
import com.pwb.backend.modules.audio.security.IpHashUtil;
import com.pwb.backend.modules.share.dto.response.SharedThreadResponse;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import com.pwb.backend.modules.share.service.SharedStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/demos/shared")
@RequiredArgsConstructor
public class SharedThreadStreamController {

    private static final String MSG_STREAM_CONFIG_FETCHED = "STREAM_CONFIG_FETCHED";

    private final SharedStreamService sharedStreamService;
    private final DemoDistributionRepository demoDistributionRepository;
    private final StreamProperties streamProperties;
    private final MessageSource messageSource;
    private final HttpClientContextResolver clientContextResolver;
    private final IpHashUtil ipHashUtil;

    @GetMapping("/{shareToken}")
    public ResponseEntity<ApiResponse<SharedThreadResponse>> getSharedThread(
            @PathVariable UUID shareToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        SharedThreadResponse data = sharedStreamService.loadSharedThread(shareToken, request);
        DemoDistribution distribution = demoDistributionRepository.findByShareToken(shareToken)
                .orElseThrow();
        String cookieValue = sharedStreamService.issueSessionCookie(
                shareToken, distribution.getDemoId(), request);
        response.addHeader("Set-Cookie", buildCookieHeader(cookieValue));
        String ipHash = ipHashUtil.hash(clientContextResolver.resolveIp(request));
        log.info("SECURE_COOKIE_ISSUED token={} demoId={} ipHash={} ipSubnet={}",
                shareToken, distribution.getDemoId(), ipHash,
                cookieSignerIpSubnet(request));
        return ResponseEntity.ok(ApiResponse.success(message(MSG_STREAM_CONFIG_FETCHED), data));
    }

    private String cookieSignerIpSubnet(HttpServletRequest request) {
        try {
            return sharedStreamService.cookieSubnetFor(request);
        } catch (Exception ex) {
            return "unresolved";
        }
    }

    private String buildCookieHeader(String cookieValue) {
        long maxAge = streamProperties.getCookieTtlSeconds();
        StringBuilder sb = new StringBuilder();
        sb.append(streamProperties.getCookieName()).append('=').append(cookieValue);
        sb.append("; Max-Age=").append(maxAge);
        sb.append("; Path=/");
        sb.append("; HttpOnly");
        if (streamProperties.isCookieSecure()) {
            sb.append("; Secure");
        }
        sb.append("; SameSite=").append(streamProperties.getCookieSameSite());
        return sb.toString();
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
