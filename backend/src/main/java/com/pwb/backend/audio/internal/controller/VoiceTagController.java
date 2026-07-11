package com.pwb.backend.audio.internal.controller;

import com.pwb.backend.audio.internal.api.CreateVoiceTagRequest;
import com.pwb.backend.audio.internal.api.CreateVoiceTagResponse;
import com.pwb.backend.audio.internal.api.VoiceTagListResponse;
import com.pwb.backend.audio.internal.api.VoiceTagPreviewResponse;
import com.pwb.backend.audio.internal.helper.CurrentUserResolver;
import com.pwb.backend.audio.internal.service.VoiceTagService;
import com.pwb.backend.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/voice-tags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER_PRO')")
public class VoiceTagController {

  private final VoiceTagService voiceTagService;
  private final CurrentUserResolver currentUserResolver;

  @PostMapping
  public ResponseEntity<ApiResponse<CreateVoiceTagResponse>> create(
      @RequestHeader("Authorization") String authHeader,
      @Valid @RequestBody CreateVoiceTagRequest request) {
    String userId = currentUserResolver.requireUserId(authHeader);
    log.info("VOICE_TAG_CREATE_REQUEST userId={} lang={}", userId, request.languageCode());
    CreateVoiceTagResponse response = voiceTagService.createVoiceTag(userId, request);
    return ResponseEntity.status(201)
        .body(ApiResponse.success("Voice tag created", response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<VoiceTagListResponse>> list(
      @RequestHeader("Authorization") String authHeader) {
    String userId = currentUserResolver.requireUserId(authHeader);
    return ResponseEntity.ok(ApiResponse.success(
        "Voice tags retrieved", voiceTagService.listVoiceTags(userId)));
  }

  @GetMapping("/{id}/preview")
  public ResponseEntity<ApiResponse<VoiceTagPreviewResponse>> preview(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable String id) {
    String userId = currentUserResolver.requireUserId(authHeader);
    VoiceTagPreviewResponse response = voiceTagService.previewVoiceTag(userId, id);
    return ResponseEntity.ok(ApiResponse.success("Preview URL generated", response));
  }

  @PostMapping("/{id}/default")
  public ResponseEntity<ApiResponse<Void>> setDefault(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable String id) {
    String userId = currentUserResolver.requireUserId(authHeader);
    voiceTagService.setDefaultVoiceTag(userId, id);
    return ResponseEntity.ok(ApiResponse.success("Default voice tag updated", null));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable String id) {
    String userId = currentUserResolver.requireUserId(authHeader);
    voiceTagService.softDeleteVoiceTag(userId, id);
    return ResponseEntity.ok(ApiResponse.success("Voice tag deleted", null));
  }
}
