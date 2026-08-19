package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.CreateVoiceTagTtsRequest;
import com.pwb.audio.api.dto.request.PreviewVoiceTagTtsRequest;
import com.pwb.audio.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.audio.api.dto.response.AudioUrlResponse;
import com.pwb.audio.api.dto.response.TtsVoiceResponse;
import com.pwb.audio.api.dto.response.VoiceTagResponse;
import com.pwb.audio.api.dto.response.VoiceTagSuggestionResponse;
import com.pwb.audio.application.command.DeleteVoiceTagCommand;
import com.pwb.audio.application.command.UpdateVoiceTagCommand;
import com.pwb.audio.application.command.VoiceTagAudioUpload;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.usecase.VoiceTagSearchUseCase;
import com.pwb.audio.application.usecase.VoiceTagUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.TtsPreview;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
import com.pwb.audio.infrastructure.audio.properties.VoiceTagUploadProperties;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.dto.PageResponse;
import com.pwb.web.dto.PageResponses;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Every route below is authenticated by the security filter chain, so {@code userId} is always present.
 *
 * <p>The routes that mint new audio additionally require the PRO role — see {@link SongController} for the
 * reasoning behind guarding creation rather than everything. Both synthesis routes matter more than the
 * rest put together: each call is a billed Google request, and {@code /tts/preview} stores nothing, so
 * before this guard existed any authenticated account could spend the project's TTS budget in a loop with
 * nothing to show for it afterwards. The per-endpoint rate limit in {@code application.yml} narrows that
 * tap; it does not decide who is allowed to open it.
 *
 * <p>{@code /tts/voices} is deliberately left to any authenticated caller: it returns a hard-coded catalog
 * from {@code GoogleTtsAdapter} without contacting Google, so it costs nothing and reveals nothing.
 */
@RestController
@RequestMapping("/api/v1/voice-tags")
@RequiredArgsConstructor
@Validated
public class VoiceTagController {

    private static final String MSG_VOICE_TAG_UPDATED = "AUDIO_VOICE_TAG_UPDATED";
    private static final String MSG_TTS_VOICE_TAG_CREATED = "AUDIO_TTS_VOICE_TAG_CREATED";
    private static final String MSG_UPLOADED_VOICE_TAG_CREATED = "AUDIO_UPLOADED_VOICE_TAG_CREATED";

    private static final Duration AUDIO_URL_EXPIRATION = Duration.ofHours(1);

    private static final String HEADER_PREVIEW_DURATION = "X-Preview-Duration-Seconds";

    private static final int MAX_SUGGESTION_LIMIT = 20;

    private final VoiceTagUseCase voiceTagUseCase;
    private final VoiceTagSearchUseCase voiceTagSearchUseCase;
    private final MessageResolver messageResolver;
    private final VoiceTagUploadProperties voiceTagUploadProperties;

    @PostMapping("/tts")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> createVoiceTagTts(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateVoiceTagTtsRequest request
    ) {
        VoiceTagView view = voiceTagUseCase.createVoiceTagTts(
                userId,
                request.name(),
                request.text(),
                request.languageCode(),
                request.voiceName()
        );
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_TTS_VOICE_TAG_CREATED), body));
    }

    /**
     * Registers a clip the user recorded elsewhere. Multipart rather than the presigned-upload dance the
     * songs use: the clip is a few seconds long, and the server has to read the bytes anyway to measure
     * the duration before it will accept them.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<VoiceTagResponse>> createVoiceTagUpload(
            @CurrentUser UUID userId,
            @RequestParam("name") @NotBlank @Size(max = 100) String name,
            @RequestParam("file") MultipartFile file
    ) {
        VoiceTagView view = voiceTagUseCase.createVoiceTagUpload(userId, name, toUpload(file));
        VoiceTagResponse body = VoiceTagResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_UPLOADED_VOICE_TAG_CREATED), body));
    }

    /**
     * Adapts Spring's multipart type into the framework-free record the application layer works with.
     *
     * <p>The size is checked against the voice tag limit <em>before</em> {@code getBytes()}, not after.
     * Spring's own ceiling is {@code spring.servlet.multipart.max-file-size}, ten times this limit, so
     * reading first meant a handful of concurrent oversized posts each allocated a hundred-megabyte array
     * and only then learned the upload was never going to be accepted.
     */
    private VoiceTagAudioUpload toUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AudioBusinessException(AudioErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > voiceTagUploadProperties.getMaxFileSizeBytes()) {
            throw new AudioBusinessException(AudioErrorCode.FILE_TOO_LARGE);
        }
        try {
            return new VoiceTagAudioUpload(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE, ex);
        }
    }

    /**
     * Returns the audio itself rather than an envelope: the client feeds it straight to an audio element,
     * and nothing was stored that a URL could point at.
     */
    @PostMapping("/tts/preview")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<byte[]> previewVoiceTagTts(
            @Valid @RequestBody PreviewVoiceTagTtsRequest request
    ) {
        TtsPreview preview = voiceTagUseCase.previewVoiceTagTts(
                request.text(),
                request.languageCode(),
                request.voiceName()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HEADER_PREVIEW_DURATION, String.valueOf(
                        preview.durationSeconds() == null ? 0 : preview.durationSeconds()))
                .body(preview.audioBytes());
    }

    @GetMapping("/tts/voices")
    public ResponseEntity<ApiResponse<List<TtsVoiceResponse>>> listTtsVoices() {
        List<TtsVoiceResponse> body = voiceTagUseCase.listAvailableVoices().stream()
                .map(TtsVoiceResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoiceTagResponse>>> listVoiceTags(
            @CurrentUser UUID userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<VoiceTagView> page = voiceTagUseCase.listVoiceTags(userId, pageable);
        PageResponse<VoiceTagResponse> body = PageResponses.from(page, VoiceTagResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * Search over the caller's own voice tags.
     *
     * <p>Only the name is matched. The synthesis source text is not searched: a tag is found by what its
     * owner called it, not by the words it happens to say. The name is matched as a plain substring, so
     * there is no tolerance for typos or for missing Vietnamese diacritics.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<VoiceTagResponse>>> searchVoiceTags(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) VoiceTagType tagType,
            @RequestParam(required = false) String languageCode,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        VoiceTagSearchCriteria criteria = new VoiceTagSearchCriteria(userId, q, tagType, languageCode);
        Page<VoiceTagView> page = voiceTagSearchUseCase.search(criteria, pageable);
        PageResponse<VoiceTagResponse> body = PageResponses.from(page, VoiceTagResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * Search-as-you-type for the voice tag picker in the song upload form. Each row carries the voice and
     * language alongside the name, so the dropdown can show what a tag sounds like without one request per
     * suggestion.
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<VoiceTagSuggestionResponse>>> suggestVoiceTags(
            @CurrentUser UUID userId,
            @RequestParam String q,
            @RequestParam(required = false) VoiceTagType tagType,
            @RequestParam(defaultValue = "8") @Min(1) @Max(MAX_SUGGESTION_LIMIT) int limit
    ) {
        VoiceTagSearchCriteria criteria = new VoiceTagSearchCriteria(userId, q, tagType, null);
        List<VoiceTagSuggestionResponse> body = voiceTagSearchUseCase.suggest(criteria, limit).stream()
                .map(VoiceTagSuggestionResponse::from)
                .toList();
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
