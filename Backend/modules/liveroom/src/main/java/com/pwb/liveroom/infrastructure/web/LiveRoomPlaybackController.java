package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import com.pwb.backend.security.CurrentUser;
import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
import com.pwb.liveroom.api.dto.request.SelectPlaybackSongRequest;
import com.pwb.liveroom.api.dto.response.PlaybackSnapshotResponse;
import com.pwb.liveroom.api.dto.response.SharedStreamUrlResponse;
import com.pwb.liveroom.core.service.LiveRoomPlaybackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/live-rooms/{roomCode}/playback")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'PRO', 'ADMIN')")
public class LiveRoomPlaybackController {

    private static final String MSG_SONG_SELECTED = "LIVEROOM_PLAYBACK_SONG_SELECTED";
    private static final String MSG_PLAYING = "LIVEROOM_PLAYBACK_PLAYING";
    private static final String MSG_PAUSED = "LIVEROOM_PLAYBACK_PAUSED";
    private static final String MSG_RETRIEVED = "LIVEROOM_PLAYBACK_RETRIEVED";
    private static final String MSG_STREAM_URL_RETRIEVED = "LIVEROOM_PLAYBACK_STREAM_URL_RETRIEVED";
    private static final String PATH_ROOM_CODE = "roomCode";
    private static final String PATH_SONG_ID = "songId";
    private static final Duration DEFAULT_STREAM_TTL = Duration.ofHours(1);

    private final LiveRoomPlaybackService playbackService;
    private final MessageResolver messageResolver;

    @GetMapping
    public ApiResponse<PlaybackSnapshotResponse> get(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        PlaybackSnapshotResponse data = playbackService.getSnapshot(user.getId(), roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_RETRIEVED));
    }

    @PostMapping("/songs")
    public ResponseEntity<ApiResponse<PlaybackSnapshotResponse>> selectSong(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @Valid @RequestBody SelectPlaybackSongRequest request) {
        PlaybackSnapshotResponse data = playbackService.selectSong(
                user.getId(), roomCode, request.getSongId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_SONG_SELECTED)));
    }

    @PostMapping("/play")
    public ApiResponse<PlaybackSnapshotResponse> play(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        PlaybackSnapshotResponse data = playbackService.play(user.getId(), roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_PLAYING));
    }

    @PostMapping("/pause")
    public ApiResponse<PlaybackSnapshotResponse> pause(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode) {
        PlaybackSnapshotResponse data = playbackService.pause(user.getId(), roomCode);
        return ApiResponse.success(data, messageResolver.get(MSG_PAUSED));
    }

    @GetMapping("/songs/{songId}/stream")
    public ApiResponse<SharedStreamUrlResponse> sharedStreamUrl(
            @CurrentUser AuthenticatedUser user,
            @PathVariable(name = PATH_ROOM_CODE) String roomCode,
            @PathVariable(name = PATH_SONG_ID) UUID songId,
            @RequestParam(name = "ttlSeconds", required = false) Long ttlSeconds) {
        Duration expiration = ttlSeconds == null || ttlSeconds <= 0
                ? DEFAULT_STREAM_TTL
                : Duration.ofSeconds(Math.min(ttlSeconds, DEFAULT_STREAM_TTL.getSeconds()));
        URL url = playbackService.getSharedSongStreamUrl(user.getId(), roomCode, songId, expiration);
        SharedStreamUrlResponse data = SharedStreamUrlResponse.builder()
                .url(url)
                .expiresAt(Instant.now().plus(expiration))
                .build();
        return ApiResponse.success(data, messageResolver.get(MSG_STREAM_URL_RETRIEVED));
    }
}
