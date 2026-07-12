package com.pwb.backend.modules.share.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.share.service.DemoDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
public class RecipientSuggestController {

    private static final String MSG_RECIPIENTS_SUGGESTED = "RECIPIENTS_SUGGESTED";

    private final DemoDistributionService demoDistributionService;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;

    @GetMapping("/recipients/suggest")
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<List<String>>> suggestRecipients(
            @RequestParam("q") String keyword) {
        UUID producerId = currentUserResolver.resolveUserId();
        List<String> data = demoDistributionService.suggestRecipients(keyword, producerId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_RECIPIENTS_SUGGESTED), data));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}