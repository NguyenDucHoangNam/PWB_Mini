package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.common.security.cookie.RefreshTokenCookieWriter;
import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.modules.iam.dto.response.SessionInfoResponse;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.AuthService;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.session.SessionMetadata;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private static final String MSG_SESSIONS_FETCHED = "SESSIONS_FETCHED";
    private static final String MSG_SESSION_REVOKED = "SESSION_REVOKED";
    private static final String MSG_OTHER_SESSIONS_REVOKED = "OTHER_SESSIONS_REVOKED";

    private final SessionService sessionService;
    private final AuthService authService;
    private final CurrentUserResolver currentUserResolver;
    private final RefreshTokenCookieWriter cookieWriter;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionInfoResponse>>> listSessions(HttpServletRequest httpRequest) {
        UUID userId = currentUserResolver.resolveUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        List<SessionMetadata> sessions = sessionService.listActiveSessions(userId, currentRefreshToken);
        List<SessionInfoResponse> response = sessions.stream()
                .map(meta -> new SessionInfoResponse(
                        publicSessionId(meta.refreshToken()),
                        meta.ip(),
                        meta.device(),
                        meta.location(),
                        meta.createdAt(),
                        meta.refreshToken().equals(currentRefreshToken)))
                .toList();
        log.info("LIST_SESSIONS_REQUEST userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_SESSIONS_FETCHED), response));
    }

    private static String publicSessionId(String refreshToken) {
        if (refreshToken == null || refreshToken.length() < 8) {
            return "session";
        }
        return refreshToken.substring(0, 8);
    }

    @DeleteMapping("/{tokenUuid}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable("tokenUuid") String tokenUuid,
                                                           @RequestHeader(name = "Authorization", required = false) String authHeader,
                                                           HttpServletRequest httpRequest) {
        validateUuid(tokenUuid);
        UUID userId = currentUserResolver.resolveUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        String currentAccessToken = bearerTokenExtractor.extract(authHeader);
        String currentAccessSignature = (currentAccessToken == null || currentAccessToken.isBlank())
                ? null
                : authService.blacklistAccessTokenSignature(currentAccessToken);
        try {
            sessionService.revokeSingleSessionForCurrent(userId, tokenUuid, currentRefreshToken, currentAccessSignature);
        } catch (BusinessException ex) {
            if (ex.errorCodeName() != null && ex.errorCodeName().equals(IamErrorCode.SESSION_NOT_FOUND.name())) {
                log.error("REVOKE_SESSION_UNAUTHORIZED userId={} attemptedTokenUuid={}", userId, tokenUuid);
            }
            throw ex;
        }
        return ResponseEntity.ok(ApiResponse.success(message(MSG_SESSION_REVOKED)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> revokeOtherSessions(HttpServletRequest httpRequest) {
        UUID userId = currentUserResolver.resolveUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        sessionService.revokeAllOtherSessions(userId, currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_OTHER_SESSIONS_REVOKED)));
    }

    private void validateUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(IamErrorCode.SESSION_NOT_FOUND);
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(IamErrorCode.SESSION_NOT_FOUND);
        }
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}