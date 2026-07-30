package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.CreateVoiceTagRequest;
import com.pwb.audio.api.dto.request.PresignedUrlRequest;
import com.pwb.audio.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.audio.api.dto.response.PresignedUrlResponse;
import com.pwb.audio.api.dto.response.VoiceTagResponse;
import com.pwb.audio.application.command.CreateVoiceTagCommand;
import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.facade.AudioFacade;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.dto.PageResponse;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audio/voice-tags")
@RequiredArgsConstructor
public class VoiceTagController {

    private static final String MSG_VOICE_TAG_CREATED = "AUDIO_VOICE_TAG_CREATED";
    private static final String MSG_VOICE_TAG_UPDATED = "AUDIO_VOICE_TAG_UPDATED";
    private static final String MSG_VOICE_TAG_DEFAULT = "AUDIO_VOICE_TAG_DEFAULT";
    private static final String MSG_PRESIGNED_URL = "AUDIO_PRESIGNED_URL_GENERATED";

    private final AudioFacade audioFacade;
    private final MessageResolver messageResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<VoiceTagResponse>> createVoiceTag(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateVoiceTagRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        CreateVoiceTagCommand command = toCreateCommand(userId, request);
        VoiceTagView view = audioFacade.createVoiceTag(command);
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_VOICE_TAG_CREATED), body));
    }

    @GetMapping("/{voiceTagId}")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> getVoiceTag(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId
    ) {
        if (userId == null) {
            return unauthorized();
        }
        VoiceTagView view = audioFacade.getVoiceTag(userId, voiceTagId);
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoiceTagResponse>>> listVoiceTags(
            @CurrentUser UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Page<VoiceTagView> page = audioFacade.listVoiceTags(userId, pageable);
        Page<VoiceTagResponse> mapped = page.map(VoiceTagResponse::from);
        PageResponse<VoiceTagResponse> body = PageResponse.of(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements()
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PatchMapping("/{voiceTagId}")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> updateVoiceTag(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId,
            @Valid @RequestBody UpdateVoiceTagRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        UpdateVoiceTagCommand command = toUpdateCommand(userId, voiceTagId, request);
        VoiceTagView view = audioFacade.updateVoiceTag(command);
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_VOICE_TAG_UPDATED), body));
    }

    @DeleteMapping("/{voiceTagId}")
    public ResponseEntity<ApiResponse<Void>> deleteVoiceTag(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DeleteVoiceTagCommand command = toDeleteCommand(userId, voiceTagId);
        audioFacade.deleteVoiceTag(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{voiceTagId}/mark-default")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> markDefault(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId
    ) {
        if (userId == null) {
            return unauthorized();
        }
        VoiceTagView view = audioFacade.markVoiceTagDefault(userId, voiceTagId);
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_VOICE_TAG_DEFAULT), body));
    }

    @GetMapping("/upload-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getUploadUrl(
            @CurrentUser UUID userId,
            @Valid @ModelAttribute PresignedUrlRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        PresignedUrlView view = audioFacade.getVoiceTagUploadUrl(userId, request.filename(), request.expirationSeconds());
        PresignedUrlResponse body = PresignedUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }

    private static CreateVoiceTagCommand toCreateCommand(UUID userId, CreateVoiceTagRequest request) {
        return new CreateVoiceTagCommand(
                userId,
                request.name(),
                request.tagType(),
                request.sourceText(),
                request.languageCode(),
                request.s3Key(),
                request.durationSeconds(),
                request.fileSizeBytes()
        );
    }

    private static UpdateVoiceTagCommand toUpdateCommand(UUID userId, UUID voiceTagId, UpdateVoiceTagRequest request) {
        return new UpdateVoiceTagCommand(
                userId,
                voiceTagId,
                request.name(),
                request.sourceText(),
                request.languageCode()
        );
    }

    private static DeleteVoiceTagCommand toDeleteCommand(UUID userId, UUID voiceTagId) {
        return new DeleteVoiceTagCommand(userId, voiceTagId);
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
