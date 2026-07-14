package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.modules.iam.dto.request.DeleteAccountRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.service.AccountDeletionService;
import com.pwb.backend.modules.iam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/account")
@RequiredArgsConstructor
public class AccountLifecycleController {

    private static final String MSG_DELETION_SCHEDULED = "ACCOUNT_DELETION_SCHEDULED";
    private static final String MSG_DELETION_CANCELLED = "ACCOUNT_DELETION_CANCELLED";

    private final AccountDeletionService accountDeletionService;
    private final CurrentUserResolver currentUserResolver;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final AuthService authService;
    private final MessageSource messageSource;

    @Value("${app.iam.account-deletion.grace-days}")
    private int graceDays;

    @Value("${app.security.jwt.access-token-ttl-seconds}")
    private long accessTokenTtlSeconds;

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteAccount(@Valid @RequestBody DeleteAccountRequest request,
                                                                         @RequestHeader(name = "Authorization", required = false) String authHeader) {
        UUID userId = currentUserResolver.resolveUserId();
        String accessToken = bearerTokenExtractor.extract(authHeader);
        String accessTokenFingerprint = authService.blacklistAccessTokenSignature(accessToken);
        UserProfileResponse profile = accountDeletionService.requestDeletion(userId, request, accessTokenFingerprint, accessTokenTtlSeconds);
        return ResponseEntity.ok(ApiResponse.success(
                message(MSG_DELETION_SCHEDULED, graceDays),
                Map.of(
                        "status", profile.status(),
                        "deletionRequestedAt", profile.deletionRequestedAt() == null
                                ? ""
                                : profile.deletionRequestedAt().toString())));
    }

    @PostMapping("/cancel-deletion")
    public ResponseEntity<ApiResponse<UserProfileResponse>> cancelDeletion() {
        UUID userId = currentUserResolver.resolveUserId();
        UserProfileResponse data = accountDeletionService.cancelDeletion(userId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_DELETION_CANCELLED), data));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}