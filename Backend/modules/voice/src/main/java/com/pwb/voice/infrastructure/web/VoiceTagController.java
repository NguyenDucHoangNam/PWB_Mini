package com.pwb.voice.infrastructure.web;

import com.pwb.kernel.security.AuthenticatedUser;
import com.pwb.web.security.CurrentUser;
import com.pwb.web.ApiResponse;
import com.pwb.web.MessageResolver;
import com.pwb.voice.api.VoiceTagFacade;
import com.pwb.voice.api.dto.request.CreateTtsVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.voice.api.dto.request.UploadVoiceTagRequest;
import com.pwb.voice.api.dto.response.AudioUrlResponse;
import com.pwb.voice.api.dto.response.VoiceTagResponse;
import com.pwb.voice.api.enums.VoiceTagType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice-tags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PRO')")
public class VoiceTagController {

    private static final String MSG_CREATED = "VOICE_TAG_CREATED";
    private static final String MSG_UPDATED = "VOICE_TAG_UPDATED";
    private static final String MSG_DELETED = "VOICE_TAG_DELETED";
    private static final String PATH_ID = "id";
    private static final Duration PRESIGNED_URL_TTL = Duration.ofHours(1);

    private final VoiceTagFacade voiceTagFacade;
    private final MessageResolver messageResolver;

    @PostMapping("/tts")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> createTts(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody CreateTtsVoiceTagRequest request) {
        VoiceTagResponse data = voiceTagFacade.createTtsTag(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_CREATED)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VoiceTagResponse>> upload(
            @CurrentUser AuthenticatedUser user,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UploadVoiceTagRequest request) {
        VoiceTagResponse data = voiceTagFacade.uploadTag(user.getId(), file, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_CREATED)));
    }

    @GetMapping
    public ApiResponse<Page<VoiceTagResponse>> list(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(name = "type", required = false) VoiceTagType type,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<VoiceTagResponse> data = voiceTagFacade.listTags(user.getId(), type, pageable);
        return ApiResponse.success(data, null);
    }

    @GetMapping("/{id}")
    public ApiResponse<VoiceTagResponse> get(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        VoiceTagResponse data = voiceTagFacade.getTag(user.getId(), id);
        return ApiResponse.success(data, null);
    }

    @PutMapping("/{id}")
    public ApiResponse<VoiceTagResponse> update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id,
            @Valid @RequestBody UpdateVoiceTagRequest request) {
        VoiceTagResponse data = voiceTagFacade.updateTag(user.getId(), id, request);
        return ApiResponse.success(data, messageResolver.get(MSG_UPDATED));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        voiceTagFacade.deleteTag(user.getId(), id);
        return ApiResponse.success(null, messageResolver.get(MSG_DELETED));
    }

    @GetMapping("/{id}/audio")
    public ApiResponse<AudioUrlResponse> getAudioUrl(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        URL url = voiceTagFacade.getAudioPresignedUrl(user.getId(), id, PRESIGNED_URL_TTL);
        AudioUrlResponse data = AudioUrlResponse.builder()
                .url(url)
                .expiresAt(Instant.now().plus(PRESIGNED_URL_TTL))
                .build();
        return ApiResponse.success(data, null);
    }
}