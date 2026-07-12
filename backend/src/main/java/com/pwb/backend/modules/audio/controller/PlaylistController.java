package com.pwb.backend.modules.audio.controller;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.config.StreamProperties;
import com.pwb.backend.modules.audio.security.PlaylistSigner;
import com.pwb.backend.modules.audio.security.PlaylistSigner.VerifyResult;
import com.pwb.backend.modules.audio.service.PlaylistService;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistSigner playlistSigner;
    private final PlaylistService playlistService;
    private final DemoDistributionRepository demoDistributionRepository;
    private final StreamProperties streamProperties;

    @GetMapping(value = "/{shareToken}/playlist.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getPlaylist(
            @PathVariable UUID shareToken,
            @RequestParam(value = "sig", required = false) String sig,
            @RequestParam(value = "exp", required = false, defaultValue = "0") long exp,
            @RequestParam(value = "nonce", required = false) String nonce) {

        DemoDistribution distribution = demoDistributionRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.PLAYLIST_SIGNATURE_INVALID,
                        "Playlist signature rejected: link not found"));

        VerifyResult result = playlistSigner.verify(shareToken, distribution.getDemoId(), sig, exp, nonce);
        if (result instanceof VerifyResult.Ok) {
            logPlaylistAccess(shareToken, distribution, "ok");
        } else if (result instanceof VerifyResult.Expired) {
            logPlaylistAccess(shareToken, distribution, "expired");
            throw new BusinessException(AudioErrorCode.PLAYLIST_SIGNATURE_EXPIRED);
        } else if (result instanceof VerifyResult.Invalid || result instanceof VerifyResult.MissingSignature) {
            logPlaylistAccess(shareToken, distribution, "invalid");
            throw new BusinessException(AudioErrorCode.PLAYLIST_SIGNATURE_INVALID);
        }

        String body = playlistService.buildM3U8(shareToken, distribution);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/{shareToken}/playlist-signature")
    public ResponseEntity<PlaylistSigner.SignedPlaylist> signPlaylist(@PathVariable UUID shareToken) {
        DemoDistribution distribution = demoDistributionRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.PLAYLIST_SIGNATURE_INVALID,
                        "Cannot sign playlist: link not found"));

        PlaylistSigner.SignedPlaylist signed = playlistSigner.sign(shareToken, distribution.getDemoId());
        long ttl = Math.min(streamProperties.getPlaylistSignatureTtlSeconds(),
                streamProperties.getPlaylistCacheTtlSeconds());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(ttl)).cachePrivate())
                .body(signed);
    }

    private void logPlaylistAccess(UUID shareToken, DemoDistribution distribution, String reason) {
        log.warn("PLAYLIST_ACCESS token={} demoId={} reason={}",
                shareToken, distribution.getDemoId(), reason);
    }
}
