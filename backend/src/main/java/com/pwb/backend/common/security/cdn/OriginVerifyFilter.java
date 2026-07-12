package com.pwb.backend.common.security.cdn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.modules.audio.config.StreamProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OriginVerifyFilter extends OncePerRequestFilter {

    private static final String HEADER_ORIGIN_VERIFY = "X-Origin-Verify";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PLAYLIST_PATH_PATTERN = "/api/v1/stream/*/playlist.m3u8";
    private static final String CODE_ORIGIN_REJECTED = "ORIGIN_REJECTED";
    private static final String MESSAGE_ORIGIN_REJECTED = "Origin verification failed";

    private final StreamProperties streamProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldVerify(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String signature = request.getHeader(HEADER_ORIGIN_VERIFY);
        String secret = streamProperties.getCdnOriginVerifySecret();
        if (signature == null || signature.isBlank() || secret == null || secret.isBlank()) {
            log.warn("PLAYLIST_SIG_REJECTED reason=missing_origin_verify path={}",
                    request.getRequestURI());
            writeRejected(response, "missing-or-empty-signature");
            return;
        }
        if (!constantTimeEquals(expectedSignature(request, secret), signature)) {
            log.warn("PLAYLIST_SIG_REJECTED reason=invalid_signature path={}",
                    request.getRequestURI());
            writeRejected(response, "invalid-signature");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldVerify(HttpServletRequest request) {
        if (!streamProperties.isCdnOriginVerifyEnabled()) {
            return false;
        }
        return "GET".equalsIgnoreCase(request.getMethod())
                && pathMatcher.match(PLAYLIST_PATH_PATTERN, request.getRequestURI());
    }

    private String expectedSignature(HttpServletRequest request, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String payload = request.getRequestURI();
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute origin verify HMAC", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private void writeRejected(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail detail = new ErrorDetail(CODE_ORIGIN_REJECTED, null, MESSAGE_ORIGIN_REJECTED);
        ApiResponse<Void> body = ApiResponse.error(MESSAGE_ORIGIN_REJECTED, List.of(detail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}