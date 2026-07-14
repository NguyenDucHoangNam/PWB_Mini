package com.pwb.backend.modules.voice_tag.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.voice_tag.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagPreviewResponse;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagResponse;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagWhitelistResponse;
import com.pwb.backend.modules.voice_tag.service.VoiceTagService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagVoiceWhitelistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class VoiceTagController {

    private static final String MSG_VOICE_TAG_CREATED = "VOICE_TAG_CREATED";
    private static final String MSG_VOICE_TAG_PREVIEW = "VOICE_TAG_PREVIEW_URL_GENERATED";
    private static final String MSG_VOICE_TAG_LIST = "VOICE_TAG_LIST_FETCHED";
    private static final String MSG_VOICE_TAG_DEFAULT = "VOICE_TAG_DEFAULT_SET";
    private static final String MSG_VOICE_TAG_DELETED = "VOICE_TAG_DELETED";
    private static final String MSG_VOICE_TAG_RESTORED = "VOICE_TAG_RESTORED";

    private final VoiceTagService voiceTagService;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;
    private final VoiceTagVoiceWhitelistService voiceWhitelistService;

    @PostMapping
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> create(@Valid @RequestBody CreateVoiceTagRequest request) {
        UUID ownerId = currentUserResolver.resolveUserId();
        VoiceTagResponse data = voiceTagService.create(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_CREATED), data));
    }

    @GetMapping("/{tagId}/preview")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<VoiceTagPreviewResponse>> preview(@PathVariable("tagId") UUID tagId) {
        UUID ownerId = currentUserResolver.resolveUserId();
        VoiceTagPreviewResponse data = voiceTagService.generatePreviewUrl(ownerId, tagId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_PREVIEW), data));
    }

    @GetMapping
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<List<VoiceTagResponse>>> list() {
        UUID ownerId = currentUserResolver.resolveUserId();
        List<VoiceTagResponse> data = voiceTagService.list(ownerId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_LIST), data));
    }

    @GetMapping("/whitelist")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<VoiceTagWhitelistResponse>> whitelist(
            @RequestParam("languageCode") String languageCode) {
        List<VoiceTagWhitelistResponse.VoiceOption> options =
                voiceWhitelistService.listVoicesForLanguage(languageCode).stream()
                        .map(voice -> new VoiceTagWhitelistResponse.VoiceOption(
                                voice.getName(), voice.getSsmlGender().name()))
                        .sorted((a, b) -> a.voiceName().compareTo(b.voiceName()))
                        .toList();
        VoiceTagWhitelistResponse data = new VoiceTagWhitelistResponse(languageCode, options);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_LIST), data));
    }

    @PostMapping("/{tagId}/default")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<Void>> setDefault(@PathVariable("tagId") UUID tagId) {
        UUID ownerId = currentUserResolver.resolveUserId();
        voiceTagService.setDefault(ownerId, tagId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_DEFAULT), null));
    }

    @DeleteMapping("/{tagId}")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable("tagId") UUID tagId) {
        UUID ownerId = currentUserResolver.resolveUserId();
        voiceTagService.softDelete(ownerId, tagId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_DELETED), null));
    }

    @PostMapping("/{tagId}/restore")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable("tagId") UUID tagId) {
        UUID ownerId = currentUserResolver.resolveUserId();
        voiceTagService.restore(ownerId, tagId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_VOICE_TAG_RESTORED), null));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
