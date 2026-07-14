package com.pwb.backend.modules.audio.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.audio.dto.request.ConfirmUploadRequest;
import com.pwb.backend.modules.audio.dto.request.PresignedUrlRequest;
import com.pwb.backend.modules.audio.dto.response.ConfirmUploadResponse;
import com.pwb.backend.modules.audio.dto.response.DemoListItemResponse;
import com.pwb.backend.modules.audio.dto.response.DemoStatusResponse;
import com.pwb.backend.modules.audio.dto.response.PresignedUrlResponse;
import com.pwb.backend.modules.audio.dto.response.RotateKeyResponse;
import com.pwb.backend.modules.audio.service.DemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
public class DemoController {

    private static final String MSG_PRESIGNED_URL_GENERATED = "DEMO_PRESIGNED_URL_GENERATED";
    private static final String MSG_UPLOAD_CONFIRMED = "DEMO_UPLOAD_CONFIRMED";
    private static final String MSG_LIST_FETCHED = "DEMO_LIST_FETCHED";
    private static final String MSG_STATUS_FETCHED = "DEMO_STATUS_FETCHED";
    private static final String MSG_KEY_ROTATED = "AES_KEY_ROTATED";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final DemoService demoService;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;

    @PostMapping("/presigned-upload-url")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedUploadUrl(
            @Valid @RequestBody PresignedUrlRequest request) {
        UUID ownerId = currentUserResolver.resolveUserId();
        PresignedUrlResponse data = demoService.generatePresignedUploadUrl(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_PRESIGNED_URL_GENERATED), data));
    }

    @PostMapping("/confirm-upload")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<ConfirmUploadResponse>> confirmUpload(
            @Valid @RequestBody ConfirmUploadRequest request) {
        UUID ownerId = currentUserResolver.resolveUserId();
        ConfirmUploadResponse data = demoService.confirmUpload(ownerId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(message(MSG_UPLOAD_CONFIRMED), data));
    }

    @GetMapping
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<Page<DemoListItemResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID ownerId = currentUserResolver.resolveUserId();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DemoListItemResponse> data = demoService.list(ownerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LIST_FETCHED), data));
    }

    @GetMapping("/{demoId}/status")
    @PreAuthorize("hasAnyRole('USER','PRO','ADMIN')")
    public ResponseEntity<ApiResponse<DemoStatusResponse>> getStatus(@PathVariable("demoId") UUID demoId) {
        UUID ownerId = currentUserResolver.resolveUserId();
        DemoStatusResponse data = demoService.getStatus(ownerId, demoId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_STATUS_FETCHED), data));
    }

    @PostMapping("/{demoId}/rotate-key")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<RotateKeyResponse>> rotateKey(@PathVariable("demoId") UUID demoId) {
        UUID ownerId = currentUserResolver.resolveUserId();
        RotateKeyResponse data = demoService.rotateKey(ownerId, demoId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_KEY_ROTATED), data));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}