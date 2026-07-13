package com.pwb.backend.modules.liveroom.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.request.PlaybackSyncCommand;
import com.pwb.backend.modules.liveroom.dto.response.PlaybackStateResponse;
import com.pwb.backend.modules.liveroom.dto.ws.PlaybackUpdateMessage;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import com.pwb.backend.modules.liveroom.service.PlaybackSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackSyncServiceImpl implements PlaybackSyncService {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ACTIVE_SOURCE_ID = "activeSourceId";

    private static final String FIELD_PLAYBACK_STATE = "playbackState";
    private static final String FIELD_CURRENT_TIME = "currentTime";
    private static final String FIELD_CLIENT_SEND_TIME = "clientSendTime";
    private static final String FIELD_SERVER_TIMESTAMP = "serverTimestamp";
    private static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";

    private static final String STATE_PLAYING = "PLAYING";
    private static final String STATE_PAUSED = "PAUSED";

    private static final String ACTION_PLAY = "PLAY";
    private static final String ACTION_PAUSE = "PAUSE";
    private static final String ACTION_SEEK = "SEEK";
    private static final String ACTION_SYNC = "SYNC";
    private static final Set<String> VALID_ACTIONS = Set.of(ACTION_PLAY, ACTION_PAUSE, ACTION_SEEK, ACTION_SYNC);

    private final StringRedisTemplate stringRedisTemplate;
    private final DemoRepository demoRepository;
    private final LiveRoomMembershipNotifier notifier;
    private final LiveRoomProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PlaybackStateResponse getPlaybackState(String roomCode) {
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        if (statusHash.isEmpty()) {
            throw new BusinessException(LiveRoomErrorCode.PLAYBACK_ROOM_NOT_LIVE,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        String status = asString(statusHash.get(FIELD_STATUS));
        if (!RoomStatus.ACTIVE.name().equals(status) && !RoomStatus.INACTIVE_HOST.name().equals(status)) {
            throw new BusinessException(LiveRoomErrorCode.PLAYBACK_ROOM_NOT_LIVE,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        String activeSourceIdStr = asString(statusHash.get(FIELD_ACTIVE_SOURCE_ID));
        if (activeSourceIdStr == null || activeSourceIdStr.isBlank()) {
            throw new BusinessException(LiveRoomErrorCode.PLAYBACK_NO_ACTIVE_SOURCE,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }

        Map<Object, Object> playbackHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomPlaybackKey(roomCode));

        long serverNow = Instant.now().toEpochMilli();
        String playbackState = playbackHash.get(FIELD_PLAYBACK_STATE) != null
                ? asString(playbackHash.get(FIELD_PLAYBACK_STATE))
                : STATE_PAUSED;
        double currentTime = parseDouble(playbackHash.get(FIELD_CURRENT_TIME), 0.0);
        long clientSendTime = parseLong(playbackHash.get(FIELD_CLIENT_SEND_TIME), serverNow);
        long serverTimestamp = parseLong(playbackHash.get(FIELD_SERVER_TIMESTAMP), serverNow);

        UUID activeSourceId;
        try {
            activeSourceId = UUID.fromString(activeSourceIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn("PLAYBACK_INVALID_SOURCE_ID roomCode={} rawValue={}", roomCode, activeSourceIdStr);
            throw new BusinessException(LiveRoomErrorCode.PLAYBACK_NO_ACTIVE_SOURCE,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }

        Optional<Demo> demoOpt = demoRepository.findById(activeSourceId);
        if (demoOpt.isEmpty()) {
            log.warn("PLAYBACK_DEMO_MISSING roomCode={} demoId={}", roomCode, activeSourceId);
            cacheDuration(roomCode, 0.0, activeSourceId);
            return new PlaybackStateResponse(activeSourceId, "(unavailable)", 0.0, List.of(),
                    serverNow, playbackState, currentTime, clientSendTime, serverTimestamp,
                    false, List.of());
        }
        Demo demo = demoOpt.get();
        double duration = demo.getDuration() != null ? demo.getDuration().doubleValue() : 0.0;
        List<Double> waveform = parseWaveform(demo.getWaveformData());
        cacheDuration(roomCode, duration, activeSourceId);

        return new PlaybackStateResponse(activeSourceId, demo.getTitle(), duration, waveform,
                serverNow, playbackState, currentTime, clientSendTime, serverTimestamp,
                false, List.of());
    }

    @Override
    public void handleSyncCommand(String roomCode, UUID userId, PlaybackSyncCommand cmd) {
        if (cmd == null || cmd.action() == null) {
            log.warn("WS_SYNC_ERROR roomCode={} userId={} reason=null_payload", roomCode, userId);
            return;
        }
        String action = cmd.action().toUpperCase();
        log.info("PLAYBACK_COMMAND roomCode={} action={} position={} userId={}",
                roomCode, action, cmd.currentTime(), userId);

        if (!VALID_ACTIONS.contains(action)) {
            log.warn("WS_SYNC_ERROR roomCode={} userId={} reason=invalid_action action={}",
                    roomCode, userId, action);
            return;
        }

        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        if (statusHash.isEmpty()) {
            log.warn("PLAYBACK_DROP_ROOM_NOT_LIVE roomCode={} userId={}", roomCode, userId);
            return;
        }
        String status = asString(statusHash.get(FIELD_STATUS));
        if (!RoomStatus.ACTIVE.name().equals(status) && !RoomStatus.INACTIVE_HOST.name().equals(status)) {
            log.warn("PLAYBACK_DROP_ROOM_NOT_LIVE roomCode={} status={} userId={}",
                    roomCode, status, userId);
            return;
        }

        double duration = resolveDuration(roomCode, asString(statusHash.get(FIELD_ACTIVE_SOURCE_ID)));
        if (duration > 0.0) {
            if (cmd.currentTime() < 0.0 || cmd.currentTime() > duration) {
                log.warn("PLAYBACK_SEEK_OUT_OF_BOUNDS roomCode={} userId={} position={} duration={}",
                        roomCode, userId, cmd.currentTime(), duration);
                return;
            }
        }

        Map<Object, Object> playbackHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomPlaybackKey(roomCode));
        String currentState = playbackHash.get(FIELD_PLAYBACK_STATE) != null
                ? asString(playbackHash.get(FIELD_PLAYBACK_STATE))
                : STATE_PAUSED;

        String newState;
        if (ACTION_PLAY.equals(action) || ACTION_SYNC.equals(action)) {
            newState = STATE_PLAYING;
        } else if (ACTION_PAUSE.equals(action)) {
            newState = STATE_PAUSED;
        } else {
            newState = currentState;
        }

        long serverNow = Instant.now().toEpochMilli();
        String playbackKey = LiveRoomRedisKeys.roomPlaybackKey(roomCode);
        stringRedisTemplate.opsForHash().put(playbackKey, FIELD_PLAYBACK_STATE, newState);
        stringRedisTemplate.opsForHash().put(playbackKey, FIELD_CURRENT_TIME, Double.toString(cmd.currentTime()));
        stringRedisTemplate.opsForHash().put(playbackKey, FIELD_CLIENT_SEND_TIME, Long.toString(cmd.clientSendTime()));
        stringRedisTemplate.opsForHash().put(playbackKey, FIELD_SERVER_TIMESTAMP, Long.toString(serverNow));
        stringRedisTemplate.opsForHash().put(playbackKey, FIELD_LAST_UPDATED_BY, userId.toString());
        stringRedisTemplate.expire(playbackKey, Duration.ofSeconds(properties.getPhase2TtlSeconds()));

        PlaybackUpdateMessage.Data data = new PlaybackUpdateMessage.Data(
                action, newState, cmd.currentTime(), cmd.clientSendTime(), userId);
        notifier.notifyPlaybackUpdate(roomCode, new PlaybackUpdateMessage("PLAYBACK_UPDATED", data));

        log.info("PLAYBACK_STATE_UPDATED roomCode={} state={} position={}", roomCode, newState, cmd.currentTime());
    }

    private double resolveDuration(String roomCode, String activeSourceIdStr) {
        String cached = stringRedisTemplate.opsForValue().get(LiveRoomRedisKeys.playbackDurationCacheKey(roomCode));
        if (cached != null && !cached.isBlank()) {
            int sep = cached.indexOf('|');
            if (sep > 0) {
                try {
                    return Double.parseDouble(cached.substring(0, sep));
                } catch (NumberFormatException ex) {
                    log.warn("PLAYBACK_DURATION_CACHE_PARSE_FAILED roomCode={} raw={}", roomCode, cached);
                }
            }
        }
        if (activeSourceIdStr == null || activeSourceIdStr.isBlank()) {
            return 0.0;
        }
        try {
            UUID demoId = UUID.fromString(activeSourceIdStr);
            Optional<Demo> demoOpt = demoRepository.findById(demoId);
            if (demoOpt.isPresent() && demoOpt.get().getDuration() != null) {
                double duration = demoOpt.get().getDuration().doubleValue();
                cacheDuration(roomCode, duration, demoId);
                return duration;
            }
        } catch (IllegalArgumentException ex) {
            log.warn("PLAYBACK_DURATION_LOOKUP_INVALID_ID roomCode={} raw={}", roomCode, activeSourceIdStr);
        }
        return 0.0;
    }

    private void cacheDuration(String roomCode, double duration, UUID demoId) {
        String value = Double.toString(duration) + "|" + demoId.toString();
        stringRedisTemplate.opsForValue().set(
                LiveRoomRedisKeys.playbackDurationCacheKey(roomCode),
                value,
                Duration.ofSeconds(properties.getPhase2TtlSeconds()));
    }

    private List<Double> parseWaveform(String waveformJson) {
        if (waveformJson == null || waveformJson.isBlank()) {
            return List.of();
        }
        try {
            var node = objectMapper.readTree(waveformJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<Double> peaks = new ArrayList<>(node.size());
            for (var item : node) {
                peaks.add(item.asDouble());
            }
            return peaks;
        } catch (Exception ex) {
            log.warn("WAVEFORM_PARSE_FAILED reason={}", ex.getMessage());
            return List.of();
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static double parseDouble(Object value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}