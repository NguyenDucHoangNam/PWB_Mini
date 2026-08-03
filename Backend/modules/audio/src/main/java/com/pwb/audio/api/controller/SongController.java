package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.CreateSongRequest;
import com.pwb.audio.api.dto.request.UpdateSongRequest;
import com.pwb.audio.api.dto.request.UploadUrlRequest;
import com.pwb.audio.api.dto.response.AudioUrlResponse;
import com.pwb.audio.api.dto.response.SongResponse;
import com.pwb.audio.api.dto.response.UploadUrlResponse;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.enums.AudioVariant;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.dto.PageResponse;
import com.pwb.web.dto.PageResponses;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.time.Duration;
import java.util.UUID;

/**
 * Every route below is authenticated by the security filter chain, so {@code userId} is always present.
 */
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
@Validated
public class SongController {

    private static final String MSG_SONG_CREATED = "AUDIO_SONG_CREATED";
    private static final String MSG_SONG_UPDATED = "AUDIO_SONG_UPDATED";
    private static final String MSG_SONG_RETRIEVED = "AUDIO_SONG_RETRIEVED";
    private static final String MSG_PROCESSING_TRIGGERED = "AUDIO_PROCESSING_TRIGGERED";
    private static final String MSG_PRESIGNED_URL = "AUDIO_PRESIGNED_URL_GENERATED";

    private static final String DEFAULT_EXPIRES_IN_SECONDS = "3600";
    private static final long MIN_EXPIRES_IN_SECONDS = 60L;
    private static final long MAX_EXPIRES_IN_SECONDS = 86_400L;

    private final SongUseCase songUseCase;
    private final MessageResolver messageResolver;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> createUploadUrl(
            @CurrentUser UUID userId,
            @Valid @RequestBody UploadUrlRequest request
    ) {
        UploadUrlView view = songUseCase.createUploadUrl(userId, request.format());
        UploadUrlResponse body = UploadUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }

    /**
     * Registers a song whose audio the client has already put into storage using an upload URL.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SongResponse>> createSong(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateSongRequest request
    ) {
        SongView view = songUseCase.createSong(request.toCommand(userId));
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_SONG_CREATED), body));
    }

    @GetMapping("/{songId}")
    public ResponseEntity<ApiResponse<SongResponse>> getSong(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        SongView view = songUseCase.getSong(userId, songId);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_SONG_RETRIEVED), body));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> listSongs(
            @CurrentUser UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SongView> page = songUseCase.listSongs(userId, pageable);
        PageResponse<SongResponse> body = PageResponses.from(page, SongResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PatchMapping("/{songId}")
    public ResponseEntity<ApiResponse<SongResponse>> updateSong(
            @CurrentUser UUID userId,
            @PathVariable UUID songId,
            @Valid @RequestBody UpdateSongRequest request
    ) {
        SongView view = songUseCase.updateSong(new UpdateSongCommand(userId, songId, request.title()));
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_SONG_UPDATED), body));
    }

    @DeleteMapping("/{songId}")
    public ResponseEntity<Void> deleteSong(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        songUseCase.deleteSong(new DeleteSongCommand(userId, songId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{songId}/trigger-processing")
    public ResponseEntity<ApiResponse<SongResponse>> triggerProcessing(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        SongView view = songUseCase.triggerProcessing(userId, songId);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(messageResolver.get(MSG_PROCESSING_TRIGGERED), body));
    }

    /**
     * @param variant which rendition to serve; asking for {@code PROCESSED} before processing has finished
     *                falls back to {@code ORIGINAL}, and the response reports what was actually served.
     */
    @GetMapping("/{songId}/audio-url")
    public ResponseEntity<ApiResponse<AudioUrlResponse>> getAudioUrl(
            @CurrentUser UUID userId,
            @PathVariable UUID songId,
            @RequestParam(defaultValue = "PROCESSED") AudioVariant variant,
            @RequestParam(defaultValue = DEFAULT_EXPIRES_IN_SECONDS)
            @Min(MIN_EXPIRES_IN_SECONDS)
            @Max(MAX_EXPIRES_IN_SECONDS) long expiresIn
    ) {
        AudioUrlView view = songUseCase.getAudioUrl(userId, songId, variant, Duration.ofSeconds(expiresIn));
        AudioUrlResponse body = AudioUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }
}
