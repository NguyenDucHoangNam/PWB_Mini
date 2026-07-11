package com.pwb.backend.audio.internal.interfaces.controller;

import com.pwb.backend.audio.api.dto.request.ConfirmUploadRequest;
import com.pwb.backend.audio.api.dto.response.ConfirmUploadResponse;
import com.pwb.backend.audio.api.dto.response.DemoStatusResponse;
import com.pwb.backend.audio.api.dto.request.PresignedUrlRequest;
import com.pwb.backend.audio.api.dto.response.PresignedUrlResponse;
import com.pwb.backend.shared.web.security.CurrentUserResolver;
import com.pwb.backend.audio.internal.application.service.DemoQueryService;
import com.pwb.backend.audio.internal.application.service.DemoUploadService;
import com.pwb.backend.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER_PRO')")
public class DemoController {

  private final DemoUploadService demoUploadService;
  private final DemoQueryService demoQueryService;
  private final CurrentUserResolver currentUserResolver;
  private final com.pwb.backend.shared.web.security.JwtVerifier jwtVerifier;

  @PostMapping("/presigned-upload-url")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedUrl(
      @RequestHeader("Authorization") String authHeader,
      @Valid @RequestBody PresignedUrlRequest request) {
    String userId = currentUserResolver.requireUserId(authHeader);
    log.info("Presign requested: user={}, fileName={}, size={}",
        userId, request.fileName(), request.fileSize());
    PresignedUrlResponse response = demoUploadService.generatePresignedUrl(userId, request);
    return ResponseEntity.ok(ApiResponse.success("Presigned upload URL generated", response));
  }

  @PostMapping("/confirm-upload")
  public ResponseEntity<ApiResponse<ConfirmUploadResponse>> confirmUpload(
      @RequestHeader("Authorization") String authHeader,
      @Valid @RequestBody ConfirmUploadRequest request) {
    String userId = currentUserResolver.requireUserId(authHeader);
    String displayName = deriveDisplayName(authHeader);
    log.info("Confirm upload: user={}, s3Key={}, title={}",
        userId, request.s3Key(), request.title());
    ConfirmUploadResponse response = demoUploadService.confirmUpload(userId, displayName, request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success("Demo upload confirmed", response));
  }

  private String deriveDisplayName(String authHeader) {
    String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    String username = jwtVerifier.extractUsername(token);
    return username != null ? username : "";
  }

  @GetMapping("/{demoId}/status")
  public ResponseEntity<ApiResponse<DemoStatusResponse>> getStatus(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable String demoId) {
    String userId = currentUserResolver.requireUserId(authHeader);
    DemoStatusResponse response = demoQueryService.getStatus(demoId, userId);
    return ResponseEntity.ok(ApiResponse.success("Demo status", response));
  }
}
