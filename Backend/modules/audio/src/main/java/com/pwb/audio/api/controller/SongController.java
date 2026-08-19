package com.pwb.audio.api.controller;

import com.pwb.audio.api.dto.request.CreateSongRequest;
import com.pwb.audio.api.dto.request.UpdateSongRequest;
import com.pwb.audio.api.dto.request.UploadUrlRequest;
import com.pwb.audio.api.dto.response.AudioUrlResponse;
import com.pwb.audio.api.dto.response.SongResponse;
import com.pwb.audio.api.dto.response.SongSuggestionResponse;
import com.pwb.audio.api.dto.response.SongTagConfigResponse;
import com.pwb.audio.api.dto.response.UploadUrlResponse;
import com.pwb.audio.application.command.DeleteSongCommand;
import com.pwb.audio.application.command.UpdateSongCommand;
import com.pwb.audio.application.usecase.SongSearchUseCase;
import com.pwb.audio.application.usecase.SongUseCase;
import com.pwb.audio.application.view.AudioUrlView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.application.view.UploadUrlView;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.repository.SongSearchCriteria;
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

import java.util.List;
import java.util.UUID;

/**
 * Every route below is authenticated by the security filter chain, so {@code userId} is always present.
 *
 * <p>Beyond authentication, the routes that <em>create</em> something carry {@code @PreAuthorize} for the
 * PRO role; the ones that read or tidy up existing rows do not. The split is deliberate rather than a
 * half-finished blanket rule:
 *
 * <ul>
 *   <li>Creation is what costs money — a presigned PUT invites bytes into the bucket, and registering a
 *       song with a voice tag queues an FFmpeg render. Those are the routes a free account must not reach,
 *       and until now nothing but the frontend's own {@code useProGuard} stopped it, so a plain
 *       {@code curl} with any valid token had the paid feature set.</li>
 *   <li>Reading is harmless: every query is already scoped to the caller's own rows, so a free account
 *       sees an empty library rather than somebody else's. Guarding reads too would break the live room's
 *       song picker, which any participant may open — see
 *       {@code SelectSongUseCaseImpl}, where selecting a track is allowed for anyone in the room as long
 *       as the track is theirs.</li>
 *   <li>Editing a title and deleting stay open on purpose. A PRO account that lapses keeps the ability to
 *       reach and remove what it already uploaded; locking someone out of their own data is a worse
 *       outcome than letting them tidy it up.</li>
 * </ul>
 *
 * <p>{@code hasRole('PRO')} matches the live room's {@code Actor.isPro()} exactly, ADMIN included: neither
 * treats an administrator as a subscriber, so an ADMIN is refused here just as it is refused a new room.
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

    private static final int MAX_SUGGESTION_LIMIT = 20;

    private final SongUseCase songUseCase;
    private final SongSearchUseCase songSearchUseCase;
    private final MessageResolver messageResolver;

    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> createUploadUrl(
            @CurrentUser UUID userId,
            @Valid @RequestBody UploadUrlRequest request
    ) {
        UploadUrlView view = songUseCase.createUploadUrl(userId, request.format(), request.sizeBytes());
        UploadUrlResponse body = UploadUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }

    /**
     * Registers a song whose audio the client has already put into storage using an upload URL.
     */
    @PostMapping
    @PreAuthorize("hasRole('PRO')")
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

    /**
     * Filtering runs in the query, so the returned page counts describe the filtered set rather than the
     * caller's whole library.
     *
     * @param status repeatable; several job states can sit behind one user-facing filter, so
     *               {@code ?status=UPLOADED&status=PROCESSED} is the shape the listing UI sends. Omit it
     *               to list everything.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> listSongs(
            @CurrentUser UUID userId,
            @RequestParam(required = false) List<SongStatus> status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SongView> page = songUseCase.listSongs(userId, status, pageable);
        PageResponse<SongResponse> body = PageResponses.from(page, SongResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * Search over the caller's own library. Separate from the listing above rather than a parameter on
     * it, because this one takes a keyword and a set of narrowing filters that the listing does not, and
     * folding both shapes into one endpoint would make the accepted parameters depend on each other.
     *
     * <p>Only the title is matched, as a plain substring: the comparison is against the characters as
     * stored, so there is no tolerance for typos and "ha noi" does not find "Hà Nội".
     *
     * @param q      the search text; blank means the filters alone decide the result
     * @param status repeatable, same as on the listing
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> searchSongs(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<SongStatus> status,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) @Min(0) Integer minDuration,
            @RequestParam(required = false) @Min(0) Integer maxDuration,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        SongSearchCriteria criteria = new SongSearchCriteria(
                userId, q, status == null ? List.of() : status, format, minDuration, maxDuration);
        Page<SongView> page = songSearchUseCase.search(criteria, pageable);
        PageResponse<SongResponse> body = PageResponses.from(page, SongResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * Search-as-you-type. Answers with titles only and no paging — it exists to be called on every
     * keystroke, so it stays as small as the dropdown that renders it.
     *
     * @param status narrows the suggestions; the room's song picker passes the playable states so it never
     *               offers a track that cannot be played
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<SongSuggestionResponse>>> suggestSongs(
            @CurrentUser UUID userId,
            @RequestParam String q,
            @RequestParam(required = false) List<SongStatus> status,
            @RequestParam(defaultValue = "8") @Min(1) @Max(MAX_SUGGESTION_LIMIT) int limit
    ) {
        SongSearchCriteria criteria = new SongSearchCriteria(
                userId, q, status == null ? List.of() : status, null, null, null);
        List<SongSuggestionResponse> body = songSearchUseCase.suggest(criteria, limit).stream()
                .map(SongSuggestionResponse::from)
                .toList();
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

    /**
     * @return the configuration the song was uploaded with, or a {@code null} payload for a song uploaded
     *         without a voice tag. Read-only: the configuration is fixed at upload and never changes.
     */
    @GetMapping("/{songId}/voice-tag-config")
    public ResponseEntity<ApiResponse<SongTagConfigResponse>> getVoiceTagConfig(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        SongTagConfigResponse body = songUseCase.getVoiceTagConfig(userId, songId)
                .map(SongTagConfigResponse::from)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * Re-runs a merge that failed. Any other status is rejected — a song that finished merging is final.
     */
    @PostMapping("/{songId}/retry-processing")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<ApiResponse<SongResponse>> retryProcessing(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        SongView view = songUseCase.retryProcessing(userId, songId);
        SongResponse body = SongResponse.from(view);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(messageResolver.get(MSG_PROCESSING_TRIGGERED), body));
    }

    /**
     * The song's single playable rendition: merged with its voice tag when it has one, the plain upload
     * otherwise. Callers do not choose — a song is whatever its owner uploaded it to be.
     *
     * <p>Nor do they choose how long the returned URL lives. An {@code expiresIn} parameter used to sit
     * here accepting anything up to 24 hours, which handed the caller control over a credential that
     * cannot be revoked once issued: deleting the song, ending the subscription or disabling the account
     * does not stop a URL already handed out. The lifetime is now a server-side constant, and the client
     * never passed anything but the default anyway.
     */
    @GetMapping("/{songId}/audio-url")
    public ResponseEntity<ApiResponse<AudioUrlResponse>> getAudioUrl(
            @CurrentUser UUID userId,
            @PathVariable UUID songId
    ) {
        AudioUrlView view = songUseCase.getAudioUrl(userId, songId);
        AudioUrlResponse body = AudioUrlResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PRESIGNED_URL), body));
    }
}
