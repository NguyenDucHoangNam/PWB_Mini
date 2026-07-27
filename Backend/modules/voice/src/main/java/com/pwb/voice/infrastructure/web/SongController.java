package com.pwb.voice.infrastructure.web;

import com.pwb.kernel.security.AuthenticatedUser;
import com.pwb.web.security.CurrentUser;
import com.pwb.web.ApiResponse;
import com.pwb.web.MessageResolver;
import com.pwb.voice.api.SongFacade;
import com.pwb.voice.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateSongRequest;
import com.pwb.voice.api.dto.request.UploadSongRequest;
import com.pwb.voice.api.dto.response.AudioUrlResponse;
import com.pwb.voice.api.dto.response.ProcessingStatusResponse;
import com.pwb.voice.api.dto.response.SongDetailResponse;
import com.pwb.voice.api.dto.response.SongResponse;
import com.pwb.voice.api.dto.response.VoiceTagConfigResponse;
import com.pwb.voice.api.enums.SongStatus;
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
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PRO')")
public class SongController {

    private static final String MSG_UPLOADED = "SONG_UPLOADED";
    private static final String MSG_UPDATED = "SONG_UPDATED";
    private static final String MSG_DELETED = "SONG_DELETED";
    private static final String MSG_CONFIGURED = "VOICE_TAG_CONFIGURED";
    private static final String MSG_CONFIG_REMOVED = "VOICE_TAG_DELETED";
    private static final String PATH_ID = "id";
    private static final String PATH_SONG_ID = "songId";
    private static final Duration PRESIGNED_URL_TTL = Duration.ofHours(1);

    private final SongFacade songFacade;
    private final MessageResolver messageResolver;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SongResponse>> upload(
            @CurrentUser AuthenticatedUser user,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UploadSongRequest request) {
        SongResponse data = songFacade.uploadSong(user.getId(), file, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_UPLOADED)));
    }

    @GetMapping
    public ApiResponse<Page<SongResponse>> list(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(name = "status", required = false) SongStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SongResponse> data = songFacade.listSongs(user.getId(), status, pageable);
        return ApiResponse.success(data, null);
    }

    @GetMapping("/{id}")
    public ApiResponse<SongDetailResponse> get(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        SongDetailResponse data = songFacade.getSong(user.getId(), id);
        return ApiResponse.success(data, null);
    }

    @PutMapping("/{id}")
    public ApiResponse<SongResponse> update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id,
            @Valid @RequestBody UpdateSongRequest request) {
        SongResponse data = songFacade.updateSong(user.getId(), id, request);
        return ApiResponse.success(data, messageResolver.get(MSG_UPDATED));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        songFacade.deleteSong(user.getId(), id);
        return ApiResponse.success(null, messageResolver.get(MSG_DELETED));
    }

    @PostMapping("/{songId}/voice-tag")
    public ResponseEntity<ApiResponse<VoiceTagConfigResponse>> configureVoiceTag(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_SONG_ID) UUID songId,
            @Valid @RequestBody ConfigureVoiceTagRequest request) {
        VoiceTagConfigResponse data = songFacade.configureVoiceTag(user.getId(), songId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_CONFIGURED)));
    }

    @GetMapping("/{songId}/voice-tag")
    public ApiResponse<VoiceTagConfigResponse> getVoiceTagConfig(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_SONG_ID) UUID songId) {
        VoiceTagConfigResponse data = songFacade.getVoiceTagConfig(user.getId(), songId);
        return ApiResponse.success(data, null);
    }

    @DeleteMapping("/{songId}/voice-tag")
    public ApiResponse<Void> removeVoiceTagConfig(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_SONG_ID) UUID songId) {
        songFacade.removeVoiceTagConfig(user.getId(), songId);
        return ApiResponse.success(null, messageResolver.get(MSG_CONFIG_REMOVED));
    }

    @PostMapping("/{songId}/process")
    public ApiResponse<ProcessingStatusResponse> triggerProcessing(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_SONG_ID) UUID songId) {
        ProcessingStatusResponse data = songFacade.triggerProcessing(user.getId(), songId);
        String translated = messageResolver.get(data.getMessage());
        return ApiResponse.success(data, translated);
    }

    @GetMapping("/{songId}/status")
    public ApiResponse<ProcessingStatusResponse> getProcessingStatus(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_SONG_ID) UUID songId) {
        ProcessingStatusResponse data = songFacade.getProcessingStatus(user.getId(), songId);
        return ApiResponse.success(data, null);
    }

    @GetMapping("/{id}/stream")
    public ApiResponse<AudioUrlResponse> stream(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        URL presignedUrl = songFacade.getStreamPresignedUrl(user.getId(), id, PRESIGNED_URL_TTL);
        AudioUrlResponse data = AudioUrlResponse.builder()
                .url(presignedUrl)
                .expiresAt(Instant.now().plus(PRESIGNED_URL_TTL))
                .build();
        return ApiResponse.success(data, null);
    }

    @GetMapping("/{id}/original")
    public ApiResponse<AudioUrlResponse> original(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ID) UUID id) {
        URL presignedUrl = songFacade.getOriginalPresignedUrl(user.getId(), id, PRESIGNED_URL_TTL);
        AudioUrlResponse data = AudioUrlResponse.builder()
                .url(presignedUrl)
                .expiresAt(Instant.now().plus(PRESIGNED_URL_TTL))
                .build();
        return ApiResponse.success(data, null);
    }
}