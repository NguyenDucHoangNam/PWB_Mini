package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.common.security.captcha.CaptchaVerifier;
import com.pwb.backend.common.security.cookie.RefreshTokenCookieWriter;
import com.pwb.backend.modules.iam.dto.request.ChangePasswordRequest;
import com.pwb.backend.modules.iam.dto.request.ForgotPasswordRequest;
import com.pwb.backend.modules.iam.dto.request.ResetPasswordRequest;
import com.pwb.backend.modules.iam.service.PasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PasswordController {

    private static final String MSG_RESET_LINK_SENT = "PASSWORD_RESET_LINK_SENT";
    private static final String MSG_RESET_SUCCESSFUL = "PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private final PasswordChangeService passwordChangeService;
    private final CurrentUserResolver currentUserResolver;
    private final RefreshTokenCookieWriter cookieWriter;
    private final CaptchaVerifier captchaVerifier;
    private final MessageSource messageSource;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                           HttpServletRequest httpRequest) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest);
        passwordChangeService.requestPasswordReset(request.email());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_RESET_LINK_SENT)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordChangeService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_RESET_SUCCESSFUL)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = currentUserResolver.resolveUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        int revokedSessions = passwordChangeService.changePassword(
                userId, request.oldPassword(), request.newPassword(), currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success(
                message(MSG_PASSWORD_CHANGED),
                Map.of("revokedOtherSessions", revokedSessions)));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
