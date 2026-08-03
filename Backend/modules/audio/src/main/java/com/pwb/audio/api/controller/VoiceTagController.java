package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.CreateVoiceTagTtsRequest;
import com.pwb.audio.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.audio.api.dto.response.AudioUrlResponse;
import com.pwb.audio.api.dto.response.VoiceTagResponse;
import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.dto.PageResponse;
import com.pwb.web.dto.PageResponses;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Every route below is authenticated by the security filter chain, so {@code userId} is always present.
 */
@RestController
@RequestMapping("/api/v1/voice-tags")
@RequiredArgsConstructor
public class VoiceTagController {

    private static final String MSG_VOICE_TAG_UPDATED = "AUDIO_VOICE_TAG_UPDATED";
    private static final String MSG_TTS_VOICE_TAG_CREATED = "AUDIO_TTS_VOICE_TAG_CREATED";

    private static final Duration AUDIO_URL_EXPIRATION = Duration.ofHours(1);

    private final VoiceTagUseCase voiceTagUseCase;
    private final MessageResolver messageResolver;

    @PostMapping("/tts")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> createVoiceTagTts(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateVoiceTagTtsRequest request
    ) {
        VoiceTagView view = voiceTagUseCase.createVoiceTagTts(
                userId,
                request.name(),
                request.text(),
                request.languageCode()
        );
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_TTS_VOICE_TAG_CREATED), body));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoiceTagResponse>>> listVoiceTags(
            @CurrentUser UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<VoiceTagView> page = voiceTagUseCase.listVoiceTags(userId, pageable);
        PageResponse<VoiceTagResponse> body = PageResponses.from(page, VoiceTagResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PatchMapping("/{voiceTagId}")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> updateVoiceTag(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId,
            @Valid @RequestBody UpdateVoiceTagRequest request
    ) {
        VoiceTagView view = voiceTagUseCase.updateVoiceTag(
                new UpdateVoiceTagCommand(userId, voiceTagId, request.name())
        );
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_VOICE_TAG_UPDATED), body));
    }

    @DeleteMapping("/{voiceTagId}")
    public ResponseEntity<Void> deleteVoiceTag(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId
    ) {
        voiceTagUseCase.deleteVoiceTag(new DeleteVoiceTagCommand(userId, voiceTagId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{voiceTagId}/audio-url")
    public ResponseEntity<ApiResponse<AudioUrlResponse>> getAudioUrl(
            @CurrentUser UUID userId,
            @PathVariable UUID voiceTagId
    ) {
        AudioUrlView view = voiceTagUseCase.getVoiceTagAudioUrl(userId, voiceTagId, AUDIO_URL_EXPIRATION);
        AudioUrlResponse body = AudioUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
