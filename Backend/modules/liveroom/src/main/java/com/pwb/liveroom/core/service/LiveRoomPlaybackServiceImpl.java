package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.api.dto.response.PlaybackSnapshotResponse;
import com.pwb.liveroom.api.dto.response.SongPlaybackSummaryResponse;
import com.pwb.liveroom.api.enums.AudioSource;
import com.pwb.liveroom.api.enums.LoopMode;
import com.pwb.liveroom.api.enums.PlaybackStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomPlaybackJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomParticipantJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomPlaybackMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomParticipantJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomPlaybackJpaRepository;
import com.pwb.liveroom.infrastructure.metrics.PlaybackMetrics;
import com.pwb.voice.api.SongFacade;
import com.pwb.voice.api.dto.response.SongDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomPlaybackServiceImpl implements LiveRoomPlaybackService {

    private static final long MAX_DURATION_SECONDS = 24L * 60 * 60;
    private static final long SEEK_INCREMENT_SECONDS = 30L;
    private static final BigDecimal DEFAULT_PLAYBACK_RATE = new BigDecimal("1.00");
    private static final Set<BigDecimal> ALLOWED_PLAYBACK_RATES = Set.of(
            new BigDecimal("1.00"),
            new BigDecimal("1.50"),
            new BigDecimal("2.00")
    );

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomPlaybackJpaRepository playbackJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomPlaybackMapper playbackMapper;
    private final SongFacade songFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final LiveRoomRealtimeBroadcaster broadcaster;
    private final PlaybackMetrics metrics;

    @Override
    @Transactional(readOnly = true)
    public PlaybackSnapshotResponse getSnapshot(UUID userId, String roomCode) {
        log.debug("Getting playback snapshot: userId={}, roomCode={}", userId, roomCode);
        ensureActiveParticipant(roomCode, userId);
        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .orElse(null);
        if (entity == null) {
            return buildEmptySnapshot(roomCode);
        }
        return buildSnapshotWithSong(entity);
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse selectSong(UUID userId, String roomCode, UUID songId) {
        log.info("Selecting song for room: userId={}, roomCode={}, songId={}", userId, roomCode, songId);
        LiveRoomJpaEntity roomEntity = lockRoom(roomCode);
        ensureRoomActive(roomEntity);
        ensureHost(roomEntity, userId);

        SongDetailResponse song = songFacade.getSong(userId, songId);
        if (song.getStatus() == null) {
            log.warn("Song missing status: userId={}, roomCode={}, songId={}", userId, roomCode, songId);
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }
        String statusName = song.getStatus().name();
        if (!"UPLOADED".equals(statusName) && !"PROCESSED".equals(statusName)) {
            log.warn("Song not ready for playback: userId={}, roomCode={}, songId={}, status={}",
                    userId, roomCode, songId, statusName);
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }
        AudioSource audioSource = "PROCESSED".equals(statusName) ? AudioSource.PROCESSED : AudioSource.ORIGINAL;
        if (!userId.equals(song.getUserId())) {
            log.warn("Song owner mismatch: callerUserId={}, songOwnerUserId={}, songId={}, roomCode={}",
                    userId, song.getUserId(), songId, roomCode);
            throw new BusinessException(ErrorCode.LIVEROOM_SONG_NOT_OWNED_BY_CALLER);
        }

        Instant now = Instant.now();
        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseGet(() -> playbackJpaRepository.save(buildInitialEntity(roomCode, now, userId)));

        entity.setSongId(song.getId());
        entity.setSongOwnerUserId(song.getUserId());
        entity.setAudioSource(audioSource);
        entity.setStatus(PlaybackStatus.PAUSED);
        entity.setPositionSeconds(0L);
        entity.setEffectiveAt(now);
        entity.setChangedAt(now);
        entity.setChangedByUserId(userId);
        entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);

        LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
        log.info("AUDIT playback.select action=selectSong roomCode={} userId={} songId={} songOwnerUserId={} version={}",
                roomCode, userId, saved.getSongId(), saved.getSongOwnerUserId(), saved.getPlaybackVersion());
        metrics.increment("playback.transition.select");

        PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
        eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                roomCode,
                snapshot,
                userId,
                now));
        return snapshot;
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse play(UUID userId, String roomCode) {
        log.info("Play requested: userId={}, roomCode={}", userId, roomCode);
        ensureActiveParticipant(roomCode, userId);

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));

        if (entity.getSongId() == null) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }

        Instant now = Instant.now();
        if (entity.getStatus() != PlaybackStatus.PLAYING) {
            entity.setStatus(PlaybackStatus.PLAYING);
            entity.setEffectiveAt(now);
            entity.setChangedAt(now);
            entity.setChangedByUserId(userId);
            entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);
            LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
            log.info("AUDIT playback.play action=play roomCode={} userId={} songId={} version={} effectiveAt={}",
                    roomCode, userId, saved.getSongId(), saved.getPlaybackVersion(), now);
            metrics.increment("playback.transition.play");

            PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
            eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                    roomCode,
                    snapshot,
                    userId,
                    now));
            return snapshot;
        }
        return buildSnapshotWithSong(entity);
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse pause(UUID userId, String roomCode) {
        log.info("Pause requested: userId={}, roomCode={}", userId, roomCode);
        ensureActiveParticipant(roomCode, userId);

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));

        if (entity.getSongId() == null) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }

        Instant now = Instant.now();
        if (entity.getStatus() != PlaybackStatus.PAUSED) {
            long currentPosition = computeCurrentPositionSeconds(entity, now);
            long clampedPosition = clampPosition(currentPosition);
            entity.setStatus(PlaybackStatus.PAUSED);
            entity.setEffectiveAt(now);
            entity.setPositionSeconds(clampedPosition);
            entity.setChangedAt(now);
            entity.setChangedByUserId(userId);
            entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);
            LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
            log.info("AUDIT playback.pause action=pause roomCode={} userId={} songId={} positionSeconds={} version={}",
                    roomCode, userId, saved.getSongId(), clampedPosition, saved.getPlaybackVersion());
            metrics.increment("playback.transition.pause");

            PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
            eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                    roomCode,
                    snapshot,
                    userId,
                    now));
            return snapshot;
        }
        return buildSnapshotWithSong(entity);
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse seek(UUID userId, String roomCode, long positionSeconds) {
        log.info("Seek requested: userId={}, roomCode={}, positionSeconds={}", userId, roomCode, positionSeconds);
        ensureActiveParticipant(roomCode, userId);

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));
        if (entity.getSongId() == null) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }

        Instant now = Instant.now();
        long currentPosition = computeCurrentPositionSeconds(entity, now);
        long delta = positionSeconds > 0 ? SEEK_INCREMENT_SECONDS : -SEEK_INCREMENT_SECONDS;
        long target = clampPosition(currentPosition + delta);
        entity.setPositionSeconds(target);
        entity.setEffectiveAt(now);
        entity.setChangedAt(now);
        entity.setChangedByUserId(userId);
        entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);
        LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
        log.info("AUDIT playback.seek action=seek roomCode={} userId={} songId={} positionSeconds={} version={}",
                roomCode, userId, saved.getSongId(), target, saved.getPlaybackVersion());
        metrics.increment("playback.transition.seek");

        PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
        eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                roomCode, snapshot, userId, now));
        return snapshot;
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse setRate(UUID userId, String roomCode, BigDecimal rate) {
        log.info("Set rate requested: userId={}, roomCode={}, rate={}", userId, roomCode, rate);
        ensureActiveParticipant(roomCode, userId);
        if (rate == null || !ALLOWED_PLAYBACK_RATES.contains(rate)) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_INVALID_RATE);
        }

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));
        if (rate.equals(entity.getPlaybackRate())) {
            return buildSnapshotWithSong(entity);
        }
        Instant now = Instant.now();
        if (entity.getStatus() == PlaybackStatus.PLAYING) {
            long currentPosition = computeCurrentPositionSeconds(entity, now);
            entity.setPositionSeconds(clampPosition(currentPosition));
            entity.setEffectiveAt(now);
        }
        entity.setPlaybackRate(rate);
        entity.setChangedAt(now);
        entity.setChangedByUserId(userId);
        entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);
        LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
        log.info("AUDIT playback.rate action=setRate roomCode={} userId={} rate={} version={}",
                roomCode, userId, rate, saved.getPlaybackVersion());
        metrics.increment("playback.transition.rate");

        PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
        eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                roomCode, snapshot, userId, now));
        return snapshot;
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse setLoop(UUID userId, String roomCode, String loopMode) {
        log.info("Set loop requested: userId={}, roomCode={}, loopMode={}", userId, roomCode, loopMode);
        ensureActiveParticipant(roomCode, userId);
        LoopMode parsed;
        try {
            parsed = LoopMode.valueOf(loopMode == null ? "" : loopMode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_INVALID_LOOP);
        }

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));
        if (parsed == entity.getLoopMode()) {
            return buildSnapshotWithSong(entity);
        }
        Instant now = Instant.now();
        entity.setLoopMode(parsed);
        entity.setChangedAt(now);
        entity.setChangedByUserId(userId);
        entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);
        LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
        log.info("AUDIT playback.loop action=setLoop roomCode={} userId={} loopMode={} version={}",
                roomCode, userId, parsed, saved.getPlaybackVersion());
        metrics.increment("playback.transition.loop");

        PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
        eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                roomCode, snapshot, userId, now));
        return snapshot;
    }

    @Override
    @Transactional
    public PlaybackSnapshotResponse setShuffle(UUID userId, String roomCode, boolean enabled) {
        log.info("Set shuffle requested: userId={}, roomCode={}, enabled={}", userId, roomCode, enabled);
        ensureActiveParticipant(roomCode, userId);

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));
        if (enabled == entity.isShuffleEnabled()) {
            return buildSnapshotWithSong(entity);
        }
        Instant now = Instant.now();
        entity.setShuffleEnabled(enabled);
        entity.setChangedAt(now);
        entity.setChangedByUserId(userId);
        entity.setPlaybackVersion(entity.getPlaybackVersion() + 1);
        LiveRoomPlaybackJpaEntity saved = playbackJpaRepository.save(entity);
        log.info("AUDIT playback.shuffle action=setShuffle roomCode={} userId={} enabled={} version={}",
                roomCode, userId, enabled, saved.getPlaybackVersion());
        metrics.increment("playback.transition.shuffle");

        PlaybackSnapshotResponse snapshot = buildSnapshotWithSong(saved);
        eventPublisher.publishEvent(new PlaybackSnapshotChangedEvent(
                roomCode, snapshot, userId, now));
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public PlaybackSnapshotResponse requestPlaybackState(UUID userId, String roomCode) {
        log.debug("Playback state requested: userId={}, roomCode={}", userId, roomCode);
        if (!liveRoomJpaRepository.existsByRoomCodeAndDeletedFalse(roomCode)) {
            log.warn("Playback state request ignored: room not found, roomCode={}, userId={}", roomCode, userId);
            return null;
        }
        if (!isActiveParticipant(roomCode, userId)) {
            log.warn("Playback state request ignored: user not joined, roomCode={}, userId={}", roomCode, userId);
            return null;
        }

        LiveRoomPlaybackJpaEntity entity = playbackJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .orElse(null);
        PlaybackSnapshotResponse snapshot;
        if (entity == null) {
            snapshot = buildEmptySnapshot(roomCode);
        } else {
            snapshot = buildSnapshotWithSong(entity);
        }
        broadcaster.pushPlaybackStateToUser(roomCode, userId, snapshot);
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public SongPlaybackSummaryResponse ownerSummary(UUID userId, UUID songId, UUID expectedOwnerUserId) {
        SongDetailResponse song = songFacade.getSong(userId, songId);
        if (expectedOwnerUserId != null && !expectedOwnerUserId.equals(song.getUserId())) {
            throw new BusinessException(ErrorCode.LIVEROOM_SONG_NOT_OWNED_BY_CALLER);
        }
        return toSongSummary(song, null);
    }

    @Override
    @Transactional(readOnly = true)
    public URL getSharedSongStreamUrl(UUID userId, String roomCode, UUID songId, Duration expiration) {
        log.info("Generating shared stream URL: userId={}, roomCode={}, songId={}", userId, roomCode, songId);
        ensureActiveParticipant(roomCode, userId);

        LiveRoomPlaybackJpaEntity playback = playbackJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG));
        if (playback.getSongId() == null) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }
        if (!playback.getSongId().equals(songId)) {
            log.warn("Stream URL mismatch: requested songId={}, playback songId={}, roomCode={}, userId={}",
                    songId, playback.getSongId(), roomCode, userId);
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NO_SONG);
        }
        SongDetailResponse song = songFacade.getSong(playback.getSongOwnerUserId(), songId);
        AudioSource audioSource = playback.getAudioSource();
        if (audioSource == null) {
            audioSource = (song.getStatus() != null && "PROCESSED".equals(song.getStatus().name()))
                    ? AudioSource.PROCESSED
                    : AudioSource.ORIGINAL;
        }

        Duration safeExpiration = expiration == null ? Duration.ofHours(1) : expiration;
        URL presignedUrl;
        if (audioSource == AudioSource.ORIGINAL) {
            presignedUrl = songFacade.getOriginalPresignedUrl(
                    playback.getSongOwnerUserId(), songId, safeExpiration);
        } else {
            presignedUrl = songFacade.getStreamPresignedUrl(
                    playback.getSongOwnerUserId(), songId, safeExpiration);
        }
        metrics.increment("playback.stream.request");
        return presignedUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlaybackSnapshotChanged(PlaybackSnapshotChangedEvent event) {
        if (event == null) {
            return;
        }
        broadcaster.broadcastPlaybackEvent(event.roomCode(), event.snapshot(), event.timestamp().toString());
    }

    private LiveRoomJpaEntity lockRoom(String roomCode) {
        return liveRoomJpaRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
    }

    private void ensureActiveParticipant(String roomCode, UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_JOINED);
        }
        Optional<LiveRoomParticipantJpaEntity> participant =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (participant.isEmpty()) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_JOINED);
        }
    }

    private boolean isActiveParticipant(String roomCode, UUID userId) {
        if (userId == null || roomCode == null) {
            return false;
        }
        return participantJpaRepository.findActiveByRoomAndUser(roomCode, userId).isPresent();
    }

    private void ensureRoomActive(LiveRoomJpaEntity roomEntity) {
        if (roomEntity.getStatus() == null || !"ACTIVE".equals(roomEntity.getStatus().name())) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_ROOM_NOT_ACTIVE);
        }
    }

    private void ensureHost(LiveRoomJpaEntity roomEntity, UUID userId) {
        if (!roomEntity.getHostUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_PLAYBACK_NOT_HOST);
        }
    }

    private LiveRoomPlaybackJpaEntity buildInitialEntity(String roomCode, Instant now, UUID changedByUserId) {
        return LiveRoomPlaybackJpaEntity.builder()
                .roomCode(roomCode)
                .status(PlaybackStatus.EMPTY)
                .positionSeconds(0L)
                .effectiveAt(now)
                .playbackVersion(0L)
                .changedByUserId(changedByUserId)
                .changedAt(now)
                .playbackRate(DEFAULT_PLAYBACK_RATE)
                .loopMode(LoopMode.OFF)
                .shuffleEnabled(false)
                .build();
    }

    private long computeCurrentPositionSeconds(LiveRoomPlaybackJpaEntity entity, Instant now) {
        long secondsSince = Math.max(0L, now.getEpochSecond() - entity.getEffectiveAt().getEpochSecond());
        return entity.getPositionSeconds() + secondsSince;
    }

    private long clampPosition(long candidate) {
        long safe = candidate < 0L ? 0L : candidate;
        return Math.min(safe, MAX_DURATION_SECONDS);
    }

    private PlaybackSnapshotResponse buildEmptySnapshot(String roomCode) {
        return PlaybackSnapshotResponse.builder()
                .roomCode(roomCode)
                .status(PlaybackStatus.EMPTY.name())
                .positionSeconds(0L)
                .effectiveAt(Instant.EPOCH)
                .version(0L)
                .changedByUserId(null)
                .changedAt(Instant.EPOCH)
                .empty(true)
                .song(null)
                .playbackRate(DEFAULT_PLAYBACK_RATE)
                .loopMode(LoopMode.OFF.name())
                .shuffleEnabled(false)
                .build();
    }

    private PlaybackSnapshotResponse buildSnapshotWithSong(LiveRoomPlaybackJpaEntity entity) {
        String audioSourceName = entity.getAudioSource() == null ? null : entity.getAudioSource().name();
        PlaybackSnapshotResponse snapshot = PlaybackSnapshotResponse.builder()
                .roomCode(entity.getRoomCode())
                .status(entity.getStatus().name())
                .positionSeconds(entity.getPositionSeconds())
                .effectiveAt(entity.getEffectiveAt())
                .version(entity.getPlaybackVersion())
                .changedByUserId(entity.getChangedByUserId())
                .changedAt(entity.getChangedAt())
                .empty(entity.getSongId() == null)
                .playbackRate(entity.getPlaybackRate() == null ? DEFAULT_PLAYBACK_RATE : entity.getPlaybackRate())
                .loopMode(entity.getLoopMode() == null ? LoopMode.OFF.name() : entity.getLoopMode().name())
                .shuffleEnabled(entity.isShuffleEnabled())
                .audioSource(audioSourceName)
                .build();
        if (entity.getSongId() != null) {
            SongDetailResponse song = songFacade.getSong(entity.getSongOwnerUserId(), entity.getSongId());
            snapshot.setSong(toSongSummary(song, audioSourceName));
        }
        return snapshot;
    }

    private SongPlaybackSummaryResponse toSongSummary(SongDetailResponse song, String fallbackAudioSource) {
        if (song == null) {
            return null;
        }
        boolean processable = song.getStatus() != null && "PROCESSED".equals(song.getStatus().name());
        String audioSource = fallbackAudioSource;
        if (audioSource == null) {
            audioSource = processable ? AudioSource.PROCESSED.name() : AudioSource.ORIGINAL.name();
        }
        return SongPlaybackSummaryResponse.builder()
                .songId(song.getId())
                .ownerUserId(song.getUserId())
                .title(song.getTitle())
                .artist(song.getArtist())
                .album(song.getAlbum())
                .durationSeconds(song.getDurationSeconds())
                .format(song.getFormat())
                .processable(processable)
                .audioSource(audioSource)
                .build();
    }

    public record PlaybackSnapshotChangedEvent(
            String roomCode,
            PlaybackSnapshotResponse snapshot,
            UUID triggeredByUserId,
            Instant timestamp
    ) {}

    private static final class LiveRoomPlaybackInitial {
        static LiveRoomPlaybackJpaEntity of(String roomCode, Instant now, UUID userId) {
            return LiveRoomPlaybackJpaEntity.builder()
                    .roomCode(roomCode)
                    .songId(null)
                    .songOwnerUserId(null)
                    .status(PlaybackStatus.EMPTY)
                    .positionSeconds(0L)
                    .effectiveAt(now)
                    .playbackVersion(0L)
                    .changedByUserId(userId)
                    .changedAt(now)
                    .build();
        }

        private LiveRoomPlaybackInitial() {}
    }
}
