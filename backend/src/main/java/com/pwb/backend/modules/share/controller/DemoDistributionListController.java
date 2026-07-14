package com.pwb.backend.modules.share.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.share.dto.response.DistributionListItemResponse;
import com.pwb.backend.modules.share.service.DemoDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
public class DemoDistributionListController {

    private static final String MSG_DISTRIBUTION_LIST_FETCHED = "DISTRIBUTION_LIST_FETCHED";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final DemoDistributionService demoDistributionService;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;

    @GetMapping("/{demoId}/distributions")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<Page<DistributionListItemResponse>>> listDistributions(
            @PathVariable UUID demoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeRevoked) {
        UUID producerId = currentUserResolver.resolveUserId();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DistributionListItemResponse> data = demoDistributionService.listDistributions(
                demoId, producerId, includeRevoked, pageable);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_DISTRIBUTION_LIST_FETCHED), data));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}