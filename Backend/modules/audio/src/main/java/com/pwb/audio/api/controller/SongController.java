package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.PresignedUploadUrlRequest;
import com.pwb.audio.api.dto.request.UpdateSongRequest;
import com.pwb.audio.api.dto.request.UploadSongRequest;
import com.pwb.audio.api.dto.response.PresignedUploadUrlResponse;
import com.pwb.audio.api.dto.response.PresignedUrlResponse;
import com.pwb.audio.api.dto.response.SongResponse;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.PresignedUploadUrlView;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongView;
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

import java.util.UUID;

/**
 * Every route below is authenticated by the security filter chain, so {@code userId} is always present.
 */
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
@Validated
public class SongController {

    private static final String MSG_SONG_UPLOADED = "AUDIO_SONG_UPLOADED";
    private static final String MSG_SONG_UPDATED = "AUDIO_SONG_UPDATED";
    private static final String MSG_SONG_RETRIEVED = "AUDIO_SONG_RETRIEVED";
    private static final String MSG_PROCESSING_TRIGGERED = "AUDIO_PROCESSING_TRIGGERED";
    private static final String MSG_PRESIGNED_URL = "AUDIO_PRESIGNED_URL_GENERATED";

    private static final String DEFAULT_STREAM_EXPIRATION_SECONDS = "3600";
    private static final long MIN_STREAM_EXPIRATION_SECONDS = 60L;
    private static final long MAX_STREAM_EXPIRATION_SECONDS = 86_400L;

    private final SongUseCase songUseCase;
    private final MessageResolver messageResolver;

    @PostMapping("/presigned-upload-url")
    public ResponseEntity<ApiResponse<PresignedUploadUrlResponse>> getPresignedUploadUrl(
            @CurrentUser UUID userId,
            @Valid @RequestBody PresignedUploadUrlRequest request
    ) {
        PresignedUploadUrlView view = songUseCase.createUploadUrl(userId, request.format());
        PresignedUploadUrlResponse body = PresignedUploadUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<SongResponse>> uploadSong(
            @CurrentUser UUID userId,
            @Valid @RequestBody UploadSongRequest request
    ) {
        SongView view = songUseCase.uploadSong(request.toCommand(userId));
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_SONG_UPLOADED), body));
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

    @GetMapping(value = {"/{songId}/stream-url", "/{songId}/audio"})
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getStreamUrl(
            @CurrentUser UUID userId,
            @PathVariable UUID songId,
            @RequestParam(defaultValue = DEFAULT_STREAM_EXPIRATION_SECONDS)
            @Min(MIN_STREAM_EXPIRATION_SECONDS)
            @Max(MAX_STREAM_EXPIRATION_SECONDS) long expirationSeconds
    ) {
        PresignedUrlView view = songUseCase.getStreamPresignedUrl(userId, songId, expirationSeconds);
        PresignedUrlResponse body = PresignedUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }
}
