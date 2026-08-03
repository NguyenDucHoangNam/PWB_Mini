package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.audio.api.dto.request.PresignedUploadUrlRequest;
import com.pwb.audio.api.dto.request.UpdateSongRequest;
import com.pwb.audio.api.dto.request.UploadSongRequest;
import com.pwb.audio.api.dto.response.PresignedUploadUrlResponse;
import com.pwb.audio.api.dto.response.PresignedUrlResponse;
import com.pwb.audio.api.dto.response.SongResponse;
import com.pwb.audio.application.command.ConfigureVoiceTagCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.command.UploadSongCommand;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.facade.AudioFacade;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.infrastructure.service.StoragePort;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private static final String MSG_SONG_UPLOADED = "AUDIO_SONG_UPLOADED";
    private static final String MSG_SONG_UPDATED = "AUDIO_SONG_UPDATED";
    private static final String MSG_SONG_RETRIEVED = "AUDIO_SONG_RETRIEVED";
    private static final String MSG_PROCESSING_TRIGGERED = "AUDIO_PROCESSING_TRIGGERED";

    private static final String MSG_PRESIGNED_URL = "AUDIO_PRESIGNED_URL_GENERATED";

    private final AudioFacade audioFacade;
    private final MessageResolver messageResolver;
    private final StoragePort storagePort;


    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<SongResponse>> uploadSong(
            @CurrentUser UUID userId,
            @Valid @RequestBody UploadSongRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        UploadSongCommand command = toUploadCommand(userId, request);
        SongView view = audioFacade.uploadSong(command);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_SONG_UPLOADED), body));
    }

    @PostMapping("/presigned-upload-url")
    public ResponseEntity<ApiResponse<PresignedUploadUrlResponse>> getPresignedUploadUrl(
            @CurrentUser UUID userId,
            @Valid @RequestBody PresignedUploadUrlRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        String ext = request.format().toLowerCase();
        String s3Key = "audio/originals/" + userId + "/" + UUID.randomUUID() + "." + ext;
        java.net.URL url = storagePort.getPresignedUploadUrl(s3Key, 3600);
        PresignedUploadUrlResponse body = new PresignedUploadUrlResponse(s3Key, url, 3600);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }

    @GetMapping("/{songId}")
    public ResponseEntity<ApiResponse<SongResponse>> getSong(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        if (userId == null) {
            return unauthorized();
        }
        SongView view = audioFacade.getSong(userId, songId);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_SONG_RETRIEVED), body));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> listSongs(
            @CurrentUser UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Page<SongView> page = audioFacade.listSongs(userId, pageable);
        Page<SongResponse> mapped = page.map(SongResponse::from);
        PageResponse<SongResponse> body = PageResponse.of(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements()
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @RequestMapping(value = "/{songId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<ApiResponse<SongResponse>> updateSong(
            @CurrentUser UUID userId,
            @PathVariable UUID songId,
            @Valid @RequestBody UpdateSongRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        UpdateSongCommand command = toUpdateCommand(userId, songId, request);
        SongView view = audioFacade.updateSong(command);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_SONG_UPDATED), body));
    }

    @DeleteMapping("/{songId}")
    public ResponseEntity<ApiResponse<Void>> deleteSong(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DeleteSongCommand command = toDeleteCommand(userId, songId);
        audioFacade.deleteSong(command);
        return ResponseEntity.noContent().build();
    }



    @PostMapping("/{songId}/trigger-processing")
    public ResponseEntity<ApiResponse<SongResponse>> triggerProcessing(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        if (userId == null) {
            return unauthorized();
        }
        SongView view = audioFacade.triggerProcessing(userId, songId);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(messageResolver.get(MSG_PROCESSING_TRIGGERED), body));
    }

    @GetMapping(value = {"/{songId}/stream-url", "/{songId}/audio"})
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getStreamUrl(
            @CurrentUser UUID userId,
            @PathVariable UUID songId,
            @RequestParam(defaultValue = "3600") long expirationSeconds
    ) {
        if (userId == null) {
            return unauthorized();
        }
        PresignedUrlView view = audioFacade.getStreamPresignedUrl(userId, songId, expirationSeconds);
        PresignedUrlResponse body = PresignedUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }

    private static UploadSongCommand toUploadCommand(UUID userId, UploadSongRequest request) {
        ConfigureVoiceTagCommand vtConfig = null;
        if (request.voiceTagConfig() != null) {
            ConfigureVoiceTagRequest vt = request.voiceTagConfig();
            vtConfig = new ConfigureVoiceTagCommand(
                    null,
                    vt.voiceTagId(),
                    vt.intervalSeconds(),
                    vt.volumePercentage(),
                    vt.fadeInDurationMs(),
                    vt.fadeOutDurationMs(),
                    vt.startOffsetSeconds(),
                    vt.enabled()
            );
        }
        return new UploadSongCommand(
                userId,
                request.title(),
                request.originalS3Key(),
                request.fileSizeBytes(),
                request.durationSeconds(),
                request.format(),
                vtConfig
        );
    }

    private static UpdateSongCommand toUpdateCommand(UUID userId, UUID songId, UpdateSongRequest request) {
        return new UpdateSongCommand(userId, songId, request.title());
    }

    private static DeleteSongCommand toDeleteCommand(UUID userId, UUID songId) {
        return new DeleteSongCommand(userId, songId);
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
