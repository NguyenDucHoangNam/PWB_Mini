package com.pwb.backend.modules.share.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.share.dto.request.DistributeDemoRequest;
import com.pwb.backend.modules.share.dto.response.DistributeDemoResponse;
import com.pwb.backend.modules.share.service.DemoDistributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
public class DemoDistributionController {

    private static final String MSG_SHARE_DEMO_SENT = "SHARE_DEMO_SENT";

    private final DemoDistributionService demoDistributionService;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;

    @PostMapping("/{demoId}/distribute")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<DistributeDemoResponse>> distribute(
            @PathVariable UUID demoId,
            @Valid @RequestBody DistributeDemoRequest request) {
        UUID producerId = currentUserResolver.resolveUserId();
        DistributeDemoResponse data = demoDistributionService.distribute(demoId, request, producerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message(MSG_SHARE_DEMO_SENT), data));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}