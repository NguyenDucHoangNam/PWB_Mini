package com.pwb.audio.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.audio.api.dto.request.UpdateSongRequest;
import com.pwb.audio.api.dto.request.UploadSongMetadata;
import com.pwb.audio.api.dto.request.UploadSongRequest;
import com.pwb.audio.api.dto.response.PresignedUrlResponse;
import com.pwb.audio.api.dto.response.SongResponse;
import com.pwb.audio.api.dto.response.SongTagConfigResponse;
import com.pwb.audio.application.command.ConfigureVoiceTagCommand;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.command.UploadSongCommand;
import com.pwb.audio.application.command.UploadSongMultipartCommand;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.application.facade.AudioFacade;
import com.pwb.audio.application.view.PresignedUrlView;
import com.pwb.audio.application.view.SongTagConfigView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.model.vo.AudioFormat;
import com.pwb.audio.infrastructure.audio.AudioProbeService;
import com.pwb.audio.infrastructure.service.StoragePort;
import com.pwb.infra.storage.util.MediaTypeUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audio/songs")
@RequiredArgsConstructor
public class SongController {

    private static final String MSG_SONG_UPLOADED = "AUDIO_SONG_UPLOADED";
    private static final String MSG_SONG_UPDATED = "AUDIO_SONG_UPDATED";
    private static final String MSG_SONG_RETRIEVED = "AUDIO_SONG_RETRIEVED";
    private static final String MSG_PROCESSING_TRIGGERED = "AUDIO_PROCESSING_TRIGGERED";
    private static final String MSG_TAG_CONFIGURED = "AUDIO_TAG_CONFIGURED";
    private static final String MSG_PRESIGNED_URL = "AUDIO_PRESIGNED_URL_GENERATED";

    private final AudioFacade audioFacade;
    private final MessageResolver messageResolver;
    private final StoragePort storagePort;
    private final AudioProbeService audioProbeService;
    private final ObjectMapper objectMapper;

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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SongResponse>> uploadSongMultipart(
            @CurrentUser UUID userId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") String metadataJson
    ) throws IOException {
        if (userId == null) {
            return unauthorized();
        }
        if (file.isEmpty()) {
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE, "File must not be empty");
        }

        UploadSongMetadata metadata = parseMetadata(metadataJson);

        byte[] head = readHead(file, 4096);
        String contentType = MediaTypeUtils.detectFromBytes(head);
        if ("application/octet-stream".equals(contentType)) {
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE,
                    "Unsupported audio format. Supported formats: mp3, wav, flac");
        }

        String ext = extensionFromContentType(contentType);
        Integer durationSeconds = audioProbeService.probeDurationFromBytes(file.getBytes(), ext);

        byte[] fullBytes = file.getBytes();
        String originalS3Key = "audio/originals/" + userId + "/" + UUID.randomUUID() + "." + ext;
        storagePort.uploadBytes(originalS3Key, fullBytes, contentType);

        UploadSongMultipartCommand command = new UploadSongMultipartCommand(
                userId,
                metadata.title(),
                metadata.artist(),
                metadata.album(),
                originalS3Key,
                (long) fullBytes.length,
                durationSeconds,
                AudioFormat.of(ext)
        );

        SongView view = audioFacade.uploadSongMultipart(command);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_SONG_UPLOADED), body));
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

    @PatchMapping("/{songId}")
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

    @PostMapping("/{songId}/configure-voice-tag")
    public ResponseEntity<ApiResponse<SongTagConfigResponse>> configureVoiceTag(
            @CurrentUser UUID userId,
            @PathVariable UUID songId,
            @Valid @RequestBody ConfigureVoiceTagRequest request
    ) {
        if (userId == null) {
            return unauthorized();
        }
        ConfigureVoiceTagCommand command = toConfigCommand(userId, songId, request);
        SongTagConfigView view = audioFacade.configureVoiceTag(userId, command);
        SongTagConfigResponse body = SongTagConfigResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messageResolver.get(MSG_TAG_CONFIGURED), body));
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

    @GetMapping("/{songId}/stream-url")
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
        return new UploadSongCommand(
                userId,
                request.title(),
                request.artist(),
                request.album(),
                request.originalS3Key(),
                request.fileSizeBytes(),
                request.durationSeconds(),
                request.format()
        );
    }

    private static UpdateSongCommand toUpdateCommand(UUID userId, UUID songId, UpdateSongRequest request) {
        return new UpdateSongCommand(userId, songId, request.title(), request.artist(), request.album());
    }

    private static ConfigureVoiceTagCommand toConfigCommand(UUID userId, UUID songId, ConfigureVoiceTagRequest request) {
        return new ConfigureVoiceTagCommand(
                songId,
                request.voiceTagId(),
                request.intervalSeconds(),
                request.volumePercentage(),
                request.fadeInDurationMs(),
                request.fadeOutDurationMs(),
                request.startOffsetSeconds(),
                request.enabled()
        );
    }

    private static DeleteSongCommand toDeleteCommand(UUID userId, UUID songId) {
        return new DeleteSongCommand(userId, songId);
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private UploadSongMetadata parseMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, UploadSongMetadata.class);
        } catch (JsonProcessingException ex) {
            throw new AudioBusinessException(AudioErrorCode.INVALID_AUDIO_FILE, "Invalid metadata JSON");
        }
    }

    private byte[] readHead(MultipartFile file, int size) throws IOException {
        byte[] full = file.getBytes();
        int len = Math.min(full.length, size);
        byte[] head = new byte[len];
        System.arraycopy(full, 0, head, 0, len);
        return head;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            case "audio/flac" -> "flac";
            default -> "bin";
        };
    }
}
