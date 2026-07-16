package com.pwb.backend.controller;

import com.pwb.backend.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.dto.response.ApiResponse;
import com.pwb.backend.dto.response.PreviewResponse;
import com.pwb.backend.dto.response.VoiceTagResponse;
import com.pwb.backend.dto.response.VoiceWhitelistResponse;
import com.pwb.backend.security.CustomUserDetails;
import com.pwb.backend.service.VoiceTagService;
import com.pwb.backend.utils.helper.MessageHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice-tags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PRO') or hasRole('ADMIN')")
public class VoiceTagController {

    private static final String MSG_CREATE = "voice_tag.create.success";
    private static final String MSG_DEFAULT = "voice_tag.default.success";
    private static final String MSG_DELETE = "voice_tag.delete.success";
    private static final String MSG_LIST = "voice_tag.list.success";
    private static final String MSG_PREVIEW = "voice_tag.preview.success";
    private static final String MSG_WHITELIST = "voice_tag.whitelist.success";

    private final VoiceTagService voiceTagService;
    private final MessageHelper messageHelper;

    @PostMapping
    public ResponseEntity<ApiResponse<VoiceTagResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateVoiceTagRequest request) {
        VoiceTagResponse data = voiceTagService.create(user.getId(), user.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageHelper.get(MSG_CREATE)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoiceTagResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user) {
        List<VoiceTagResponse> data = voiceTagService.listForOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_LIST)));
    }

    @GetMapping("/whitelist")
    public ResponseEntity<ApiResponse<VoiceWhitelistResponse>> getWhitelist(
            @RequestParam("languageCode") String languageCode) {
        VoiceWhitelistResponse data = voiceTagService.getWhitelist(languageCode);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_WHITELIST)));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<PreviewResponse>> preview(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable("id") UUID tagId) {
        PreviewResponse data = voiceTagService.generatePreview(user.getId(), tagId);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_PREVIEW)));
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> setDefault(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable("id") UUID tagId) {
        VoiceTagResponse data = voiceTagService.setDefault(user.getId(), user.getUsername(), tagId);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_DEFAULT)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable("id") UUID tagId) {
        voiceTagService.delete(user.getId(), tagId);
        return ResponseEntity.ok(ApiResponse.success(null, messageHelper.get(MSG_DELETE)));
    }
}
